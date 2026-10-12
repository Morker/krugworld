package com.krugworld.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krugworld.device.DeviceService;
import com.krugworld.device.DeviceTokenFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The React app is the only client, and it talks JSON. So:
 * <ul>
 *   <li>Everything outside {@code /api} is the app's own static files and is
 *       public — the app itself asks {@code /api/auth/me} who you are.</li>
 *   <li>Every {@code /api} call needs a login, except logging in and
 *       registering. A missing login is a bare 401, never a redirect.</li>
 *   <li>Login is a normal form POST to {@code /api/auth/login}, answered with
 *       the user as JSON (200) or a bare 401.</li>
 *   <li>CSRF: the token goes out in the readable XSRF-TOKEN cookie and comes
 *       back in the X-XSRF-TOKEN header (the frontend's api client does it).</li>
 *   <li>Remember-me: a signed, year-long cookie on every login, so each device
 *       logs in once. The signing key lives in {@code app_secret} so it
 *       survives restarts — a per-boot key would log everyone out on deploy.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String REMEMBER_ME_KEY_NAME = "remember-me-key";

    /**
     * Paired devices (desk Gaby) get their own, smaller world: only
     * {@code /api/device/**}, bearer tokens instead of cookies, no session.
     * With no cookies involved there's nothing for CSRF to protect, and a
     * browser's login cookie is ignored here just as a device token is
     * ignored everywhere else. Checked first; everything else falls through
     * to the browser chain below.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain deviceFilterChain(HttpSecurity http, DeviceService devices) throws Exception {
        http
            .securityMatcher("/api/device/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/device/pair").permitAll()
                .anyRequest().hasRole("DEVICE"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((req, res, e) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
            .addFilterBefore(new DeviceTokenFilter(devices), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, RememberMeServices rememberMeServices,
                                           ObjectMapper json) throws Exception {
        // Raw (unmasked) token in the cookie, so the header the frontend
        // echoes back matches it exactly.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/status").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    CurrentUser me = (CurrentUser) auth.getPrincipal();
                    json.writeValue(res.getOutputStream(), Map.of("id", me.id(), "username", me.username()));
                })
                .failureHandler((req, res, e) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
            .rememberMe(remember -> remember.rememberMeServices(rememberMeServices))
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The CSRF token is created lazily; touching it on every request makes
     * sure the XSRF-TOKEN cookie is always there before the first POST.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            Object attr = request.getAttribute(CsrfToken.class.getName());
            if (attr instanceof Supplier<?> supplier) {
                attr = supplier.get();
            }
            if (attr instanceof CsrfToken token) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService uds, JdbcTemplate jdbc) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(
                rememberMeKey(jdbc), uds, RememberMeTokenAlgorithm.SHA256);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds((int) Duration.ofDays(365).toSeconds());
        return services;
    }

    /** Generated once, then read back on every boot; ON CONFLICT keeps two racing instances on one key. */
    private static String rememberMeKey(JdbcTemplate jdbc) {
        byte[] fresh = new byte[32];
        new SecureRandom().nextBytes(fresh);
        jdbc.update("INSERT INTO app_secret (name, value) VALUES (?, ?) ON CONFLICT (name) DO NOTHING",
                REMEMBER_ME_KEY_NAME, Base64.getEncoder().encodeToString(fresh));
        return jdbc.queryForObject("SELECT value FROM app_secret WHERE name = ?", String.class, REMEMBER_ME_KEY_NAME);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    /** Used by registration to log the new account straight in. ProviderManager also wipes the plaintext password afterwards. */
    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }
}
