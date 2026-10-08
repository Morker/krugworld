package com.krugworld;

import com.krugworld.auth.CurrentUser;
import com.krugworld.auth.User;
import com.krugworld.auth.UserRegistered;
import com.krugworld.auth.UserRepository;
import com.krugworld.steward.FakeChatProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base for integration tests: the whole app against one real Postgres
 * (shared by every test class, since the Spring context is cached). Tests
 * never assume an empty database — each makes its own uniquely named users.
 */
@SpringBootTest(properties = {
        "krugworld.scheduling.enabled=false",
        // The Steward talks to a scripted fake in tests: no network, no API cost.
        "krugworld.steward.provider=fake"})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, FakeChatProvider.class})
public abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected ApplicationEventPublisher events;
    @Autowired protected TransactionTemplate tx;

    protected static String uniqueName() {
        return "t_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** A registered account — with the same default setup /register gives. */
    protected CurrentUser newUser() {
        return newUser("irrelevant-pass");
    }

    protected CurrentUser newUser(String password) {
        User saved = tx.execute(s -> {
            User u = users.save(new User(uniqueName(), encoder.encode(password)));
            events.publishEvent(new UserRegistered(u.getId()));
            return u;
        });
        return CurrentUser.of(saved);
    }

    protected ResultActions getAs(CurrentUser who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who)));
    }

    protected ResultActions postAs(CurrentUser who, String url, Object body) throws Exception {
        return mvc.perform(post(url).with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions putAs(CurrentUser who, String url, Object body) throws Exception {
        return mvc.perform(put(url).with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions patchAs(CurrentUser who, String url, Object body) throws Exception {
        return mvc.perform(patch(url).with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions deleteAs(CurrentUser who, String url) throws Exception {
        return mvc.perform(delete(url).with(user(who)).with(csrf()));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
