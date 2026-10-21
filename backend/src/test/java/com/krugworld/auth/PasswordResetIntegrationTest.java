package com.krugworld.auth;

import com.krugworld.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends IntegrationTest {

    @Autowired FakeResetMailer mailer;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void mailIsBackOn() {
        mailer.setAvailable(true);
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private CurrentUser userWithEmail(String email, String password) throws Exception {
        CurrentUser me = newUser(password);
        patchAs(me, "/api/auth/me/preferences", Map.of("email", email)).andExpect(status().isOk());
        return me;
    }

    private ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirmReset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", password))));
    }

    private ResultActions login(CurrentUser who, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .param("username", who.username()).param("password", password));
    }

    /** Mail goes out on one background thread, in order: once this mail has arrived, every earlier request has been handled. */
    private FakeResetMailer.Sent awaitMail(String email) {
        await().atMost(Duration.ofSeconds(5)).until(() -> !mailer.sentTo(email).isEmpty());
        var all = mailer.sentTo(email);
        return all.get(all.size() - 1);
    }

    @Test
    void aLinkSetsANewPasswordAndOnlyOnce() throws Exception {
        String email = uniqueEmail();
        CurrentUser me = userWithEmail(email, "old-passphrase");

        requestReset(email).andExpect(status().isNoContent());
        FakeResetMailer.Sent mail = awaitMail(email);
        assertThat(mail.username()).isEqualTo(me.username());
        assertThat(mail.link()).contains("/reset-password?token=");

        confirmReset(mail.token(), "brand-new-passphrase").andExpect(status().isNoContent());

        login(me, "brand-new-passphrase").andExpect(status().isOk());
        login(me, "old-passphrase").andExpect(status().isUnauthorized());

        confirmReset(mail.token(), "another-passphrase")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reset_invalid"));
        login(me, "brand-new-passphrase").andExpect(status().isOk());
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        String stranger = uniqueEmail();
        String known = uniqueEmail();
        userWithEmail(known, "some-passphrase");

        requestReset(stranger).andExpect(status().isNoContent());
        requestReset(known).andExpect(status().isNoContent());

        awaitMail(known); // the stranger's request came first, so it has been dealt with by now
        assertThat(mailer.sentTo(stranger)).isEmpty();
    }

    @Test
    void theEmailMatchesIgnoringCase() throws Exception {
        String email = uniqueEmail();
        userWithEmail(email, "some-passphrase");

        requestReset(email.toUpperCase()).andExpect(status().isNoContent());
        assertThat(awaitMail(email).token()).isNotBlank();
    }

    @Test
    void askingTwiceInAMinuteSendsOneMail() throws Exception {
        String email = uniqueEmail();
        userWithEmail(email, "some-passphrase");
        String marker = uniqueEmail();
        userWithEmail(marker, "other-passphrase");

        requestReset(email).andExpect(status().isNoContent());
        requestReset(email).andExpect(status().isNoContent());
        requestReset(marker).andExpect(status().isNoContent());

        awaitMail(marker);
        assertThat(mailer.sentTo(email)).hasSize(1);
    }

    @Test
    void aNewLinkReplacesTheOldOne() throws Exception {
        String email = uniqueEmail();
        CurrentUser me = userWithEmail(email, "old-passphrase");

        requestReset(email).andExpect(status().isNoContent());
        String first = awaitMail(email).token();

        // Pretend the first link was sent five minutes ago, so the cooldown has passed.
        jdbc.update("UPDATE password_reset SET created_at = now() - interval '5 minutes' WHERE user_id = ?", me.id());
        requestReset(email).andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(5)).until(() -> mailer.sentTo(email).size() == 2);
        String second = mailer.sentTo(email).get(1).token();

        confirmReset(first, "brand-new-passphrase").andExpect(status().isBadRequest());
        confirmReset(second, "brand-new-passphrase").andExpect(status().isNoContent());
        login(me, "brand-new-passphrase").andExpect(status().isOk());
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        String email = uniqueEmail();
        CurrentUser me = userWithEmail(email, "old-passphrase");

        requestReset(email).andExpect(status().isNoContent());
        String token = awaitMail(email).token();
        jdbc.update("UPDATE password_reset SET expires_at = now() - interval '1 minute' WHERE user_id = ?", me.id());

        confirmReset(token, "brand-new-passphrase")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reset_invalid"));
        login(me, "old-passphrase").andExpect(status().isOk());
    }

    @Test
    void aGuessedTokenIsRefused() throws Exception {
        confirmReset("not-a-real-token", "brand-new-passphrase")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reset_invalid"));
    }

    @Test
    void theNewPasswordFollowsTheSameRulesAsRegistering() throws Exception {
        confirmReset("whatever", "short").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() throws Exception {
        String email = uniqueEmail();
        CurrentUser me = userWithEmail(email, "some-passphrase");

        requestReset(email).andExpect(status().isNoContent());
        String token = awaitMail(email).token();

        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset WHERE user_id = ?", String.class, me.id());
        assertThat(stored).hasSize(64).isNotEqualTo(token).doesNotContain(token);
    }

    @Test
    void withoutMailSettingsTheServerSaysSoInsteadOfPretending() throws Exception {
        mailer.setAvailable(false);
        requestReset(uniqueEmail())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("reset_unavailable"));
    }

    @Test
    void aMalformedEmailIsRejected() throws Exception {
        requestReset("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void twoAccountsCannotShareAnEmail() throws Exception {
        String email = uniqueEmail();
        userWithEmail(email, "some-passphrase");
        CurrentUser other = newUser();

        patchAs(other, "/api/auth/me/preferences", Map.of("email", email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Test
    void youCanKeepClearAndRegisterWithAnEmail() throws Exception {
        String email = uniqueEmail();
        CurrentUser me = userWithEmail(email, "some-passphrase");

        // Saving your own email again is not a clash.
        patchAs(me, "/api/auth/me/preferences", Map.of("email", email)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        patchAs(me, "/api/auth/me/preferences", Map.of("email", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());

        String registered = uniqueEmail();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", uniqueName(),
                                "password", "long-enough", "email", registered))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(registered));
    }
}
