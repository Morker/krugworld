package com.krugworld.auth;

import com.krugworld.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void anonymousApiCallsGet401NotARedirect() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + uniqueName() + "\",\"password\":\"long-enough\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registeringLogsInAndRemembersTheDevice() throws Exception {
        String name = uniqueName();
        var result = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", name, "password", "long-enough"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.skin").value("modern"))
                .andExpect(jsonPath("$.theme").value("steel"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value(name));

        Cookie remembered = result.getResponse().getCookie("remember-me");
        mvc.perform(get("/api/auth/me").cookie(remembered)).andExpect(jsonPath("$.username").value(name));
    }

    @Test
    void loginReturnsTheUserAndACookieThatOutlivesTheSession() throws Exception {
        CurrentUser me = newUser("correct-horse");

        Cookie remembered = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", me.username()).param("password", "correct-horse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(me.username()))
                .andReturn().getResponse().getCookie("remember-me");

        // No session — only what a closed-and-reopened browser still has.
        mvc.perform(get("/api/auth/me").cookie(remembered)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        CurrentUser me = newUser("correct-horse");
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", me.username()).param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedRememberMeCookieIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(new Cookie("remember-me", "Zm9yZ2VkOjk5OTk5OTk5OTk5OTk6YWJj")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameIs409IgnoringCase() throws Exception {
        CurrentUser me = newUser();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", me.username().toUpperCase(),
                                "password", "long-enough"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("username_taken"));
    }

    @Test
    void invalidRegistrationNamesTheBadFields() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a b\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void preferencesAreSavedPerAccount() throws Exception {
        CurrentUser me = newUser();
        patchAs(me, "/api/auth/me/preferences", Map.of("skin", "classic"))
                .andExpect(jsonPath("$.skin").value("classic"))
                .andExpect(jsonPath("$.theme").value("steel"));
        patchAs(me, "/api/auth/me/preferences", Map.of("skin", "neon"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theMirageSkinAndItsBackgroundsCanBeSaved() throws Exception {
        CurrentUser me = newUser();
        // Saving proves the database's CHECK constraints accept them too (V15), not just the validation.
        patchAs(me, "/api/auth/me/preferences", Map.of("skin", "mirage", "theme", "navy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skin").value("mirage"))
                .andExpect(jsonPath("$.theme").value("navy"));
        patchAs(me, "/api/auth/me/preferences", Map.of("theme", "rose"))
                .andExpect(jsonPath("$.theme").value("rose"));
        getAs(me, "/api/auth/me").andExpect(jsonPath("$.skin").value("mirage"));
    }

    @Test
    void logoutClearsTheRememberMeCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("remember-me", 0));
    }
}
