package com.krugworld.device;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer kgd_...} on device requests. A missing or
 * unknown token just leaves the request anonymous; the device filter chain
 * then answers 401. Not a Spring bean on purpose: as a bean, Boot would also
 * register it for every request in the app.
 */
public class DeviceTokenFilter extends OncePerRequestFilter {

    private final DeviceService devices;

    public DeviceTokenFilter(DeviceService devices) {
        this.devices = devices;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            devices.authenticate(header.substring(7).trim()).ifPresent(device -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        device, null, AuthorityUtils.createAuthorityList("ROLE_DEVICE")));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
