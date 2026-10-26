package com.krugworld.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.krugworld.IntegrationTest;
import com.krugworld.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushIntegrationTest extends IntegrationTest {

    @Autowired FakePushSender pushes;
    @Autowired ReminderJob reminders;
    @Autowired Clock clock;

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private static Map<String, Object> subscription(String endpoint) {
        String p256dh = WebPushCrypto.b64(WebPushCrypto.encodePublicKey(
                (ECPublicKey) WebPushCrypto.newKeyPair().getPublic()));
        return Map.of("endpoint", endpoint, "expirationTime", "null",
                "keys", Map.of("p256dh", p256dh, "auth", WebPushCrypto.b64(new byte[16])));
    }

    @Test
    void thePublicKeyIsStableAndSubscribingWorks() throws Exception {
        CurrentUser me = newUser();
        String key = body(getAs(me, "/api/push/key")).get("publicKey").asText();
        assertThat(body(getAs(me, "/api/push/key")).get("publicKey").asText()).isEqualTo(key);
        assertThat(WebPushCrypto.b64(key)).hasSize(65);

        String endpoint = endpoint();
        postAs(me, "/api/push/subscriptions", subscription(endpoint)).andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value(endpoint));
        // The same browser subscribing again is an update, not a second row.
        postAs(me, "/api/push/subscriptions", subscription(endpoint)).andExpect(status().isOk());

        getAs(me, "/api/push/subscriptions").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyRealPushServicesAreAccepted() throws Exception {
        CurrentUser me = newUser();
        for (String bad : new String[] {"http://fcm.googleapis.com/fcm/send/x", "https://169.254.169.254/latest",
                "https://localhost:8096/api", "https://fcm.googleapis.com.evil.example/x",
                "https://fcm.googleapis.com:8443/x"}) {
            postAs(me, "/api/push/subscriptions", subscription(bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bad_endpoint"));
        }
        Map<String, Object> badKeys = Map.of("endpoint", endpoint(), "keys", Map.of("p256dh", "abc", "auth", "def"));
        postAs(me, "/api/push/subscriptions", badKeys)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bad_subscription"));
    }

    @Test
    void aTestNotificationReachesEveryDeviceAndDeadOnesAreForgotten() throws Exception {
        CurrentUser me = newUser();
        String phone = endpoint();
        String laptop = endpoint();
        postAs(me, "/api/push/subscriptions", subscription(phone)).andExpect(status().isOk());
        postAs(me, "/api/push/subscriptions", subscription(laptop)).andExpect(status().isOk());
        pushes.markGone(laptop);

        postAs(me, "/api/push/test", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1)).andExpect(jsonPath("$.devices").value(2));

        JsonNode payload = json.readTree(pushes.sentTo(phone).get(0).payload());
        assertThat(payload.get("title").asText()).isEqualTo("A raven from Gaby");
        assertThat(payload.get("url").asText()).isEqualTo("/settings");
        getAs(me, "/api/push/subscriptions").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].endpoint").value(phone));
    }

    @Test
    void someoneElsesSubscriptionLooksMissing() throws Exception {
        CurrentUser owner = newUser();
        CurrentUser other = newUser();
        long id = body(postAs(owner, "/api/push/subscriptions", subscription(endpoint()))).get("id").asLong();

        deleteAs(other, "/api/push/subscriptions/" + id).andExpect(status().isNotFound());
        getAs(other, "/api/push/subscriptions").andExpect(jsonPath("$.length()").value(0));
        deleteAs(owner, "/api/push/subscriptions/" + id).andExpect(status().isNoContent());
    }

    @Test
    void anEventReminderIsSentOnceNoMatterHowOftenTheJobRuns() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        // The event must still be today, 30 minutes from now.
        assumeTrue(now.toLocalTime().isBefore(LocalTime.of(23, 0)));

        CurrentUser me = newUser();
        String phone = endpoint();
        postAs(me, "/api/push/subscriptions", subscription(phone)).andExpect(status().isOk());
        postAs(me, "/api/events", Map.of("occursOn", now.toLocalDate().toString(),
                "atTime", now.plusMinutes(30).toLocalTime().toString(), "title", "Dentist"))
                .andExpect(status().isCreated());

        reminders.run(now);
        reminders.run(now.plusMinutes(5));

        assertThat(pushes.sentTo(phone)).filteredOn(s -> s.payload().contains("Dentist")).hasSize(1);
    }

    @Test
    void aDebtDueTodayIsNudgedOnceAndATickedOneNotAtAll() throws Exception {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime morning = today.atTime(10, 0);

        CurrentUser me = newUser();
        String phone = endpoint();
        postAs(me, "/api/push/subscriptions", subscription(phone)).andExpect(status().isOk());
        postAs(me, "/api/debts", Map.of("direction", "THEY_OWE", "person", "Ali", "amount", "50.00",
                "dueOn", today.toString())).andExpect(status().isCreated());
        long paid = body(postAs(me, "/api/debts", Map.of("direction", "I_OWE", "person", "Mei", "amount", "20.00",
                "dueOn", today.toString()))).get("id").asLong();
        postAs(me, "/api/debts/" + paid + "/settle", Map.of()).andExpect(status().isOk());

        reminders.run(morning);
        reminders.run(morning.plusMinutes(5));

        assertThat(pushes.sentTo(phone)).filteredOn(s -> s.payload().contains("Ali's RM 50.00 is due today"))
                .hasSize(1);
        assertThat(pushes.sentTo(phone)).noneMatch(s -> s.payload().contains("Mei"));
    }
}
