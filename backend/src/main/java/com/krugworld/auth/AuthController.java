package com.krugworld.auth;

import com.krugworld.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.math.BigDecimal;

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
            @NotBlank @Size(min = 8, max = 100, message = "at least 8 characters") String password,
            /** Optional, but it's the only way to get a password back by email. */
            @Email @Size(max = 254) String email) {
    }

    /** Every field is optional: send only what changes. */
    public record PreferencesRequest(
            @Pattern(regexp = "classic|modern|mirage") String skin,
            @Pattern(regexp = "steel|oak|forest|parchment|navy|ocean|dusk|rose") String theme,
            @Min(500) @Max(10000) Integer calorieGoal,
            @Pattern(regexp = "[CF]") String tempUnit,
            @Valid WeatherLocation weather,
            /** Blank clears it. */
            @Email @Size(max = 254) String email) {
    }

    /** Where the Hall's weather is for. An empty place clears it. */
    public record WeatherLocation(
            @Size(max = 120) String place,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal lon) {
    }

    public record Me(Long id, String username, String email, String skin, String theme, int calorieGoal,
                     String tempUnit, WeatherLocation weather, boolean admin) {
        static Me of(User u) {
            WeatherLocation weather = u.getWeatherPlace() == null ? null
                    : new WeatherLocation(u.getWeatherPlace(), u.getWeatherLat(), u.getWeatherLon());
            return new Me(u.getId(), u.getUsername(), u.getEmail(), u.getSkin(), u.getTheme(), u.getCalorieGoal(),
                    u.getTempUnit(), weather, u.isAdmin());
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
        String email = cleanEmail(body.email());
        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken", "That email already belongs to another account.");
        }

        User saved = tx.execute(status -> {
            User created = new User(username, encoder.encode(body.password()));
            created.setEmail(email);
            User user = users.save(created);
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
        if (body.email() != null) {
            String email = cleanEmail(body.email());
            if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "email_taken", "That email already belongs to another account.");
            }
            user.setEmail(email);
        }
        if (body.skin() != null) {
            user.setSkin(body.skin());
        }
        if (body.theme() != null) {
            user.setTheme(body.theme());
        }
        if (body.calorieGoal() != null) {
            user.setCalorieGoal(body.calorieGoal());
        }
        if (body.tempUnit() != null) {
            user.setTempUnit(body.tempUnit());
        }
        if (body.weather() != null) {
            WeatherLocation w = body.weather();
            boolean clear = w.place() == null || w.place().isBlank();
            if (!clear && (w.lat() == null || w.lon() == null)) {
                throw ApiException.badRequest("weather_incomplete", "A weather place needs its coordinates.");
            }
            user.setWeatherLocation(clear ? null : w.place().trim(), clear ? null : w.lat(), clear ? null : w.lon());
        }
        return Me.of(users.save(user));
    }

    /** Trimmed; blank means "none". */
    private static String cleanEmail(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private User load(CurrentUser me) {
        return users.findById(me.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "no_account", "Account no longer exists."));
    }
}
