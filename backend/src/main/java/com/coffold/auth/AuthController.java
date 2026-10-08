package com.coffold.auth;

import com.coffold.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, "who am I", and per-account preferences. Logging in and out
 * are handled by Spring Security itself — see {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,40}$",
                    message = "3–40 letters, digits, underscores or hyphens") String username,
            @NotBlank @Size(min = 8, max = 100, message = "at least 8 characters") String password) {
    }

    public record PreferencesRequest(
            @Pattern(regexp = "classic|modern") String skin,
            @Pattern(regexp = "steel|oak|forest|parchment") String theme) {
    }

    public record Me(Long id, String username, String skin, String theme) {
        static Me of(User u) {
            return new Me(u.getId(), u.getUsername(), u.getSkin(), u.getTheme());
        }
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final RememberMeServices rememberMeServices;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager,
                          RememberMeServices rememberMeServices, ApplicationEventPublisher events,
                          TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.rememberMeServices = rememberMeServices;
        this.events = events;
        this.tx = tx;
    }

    @PostMapping("/register")
    public Me register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
                       HttpServletResponse response) {
        String username = body.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "username_taken", "That name is already carved here.");
        }

        User saved = tx.execute(status -> {
            User user = users.save(new User(username, encoder.encode(body.password())));
            events.publishEvent(new UserRegistered(user.getId()));
            return user;
        });

        // Log straight in — same session and remember-me cookie a /login gives.
        Authentication result = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, body.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        rememberMeServices.loginSuccess(request, response, result);

        return Me.of(saved);
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal CurrentUser me) {
        return Me.of(load(me));
    }

    @PatchMapping("/me/preferences")
    public Me updatePreferences(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody PreferencesRequest body) {
        User user = load(me);
        if (body.skin() != null) {
            user.setSkin(body.skin());
        }
        if (body.theme() != null) {
            user.setTheme(body.theme());
        }
        return Me.of(users.save(user));
    }

    private User load(CurrentUser me) {
        return users.findById(me.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "no_account", "Account no longer exists."));
    }
}
