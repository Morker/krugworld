package com.krugworld.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * The logged-in account as Spring Security holds it. Controllers take it via
 * {@code @AuthenticationPrincipal CurrentUser me} and scope every query by
 * {@link #id()} — that one id is what keeps accounts' data apart.
 */
public record CurrentUser(Long id, String username, String passwordHash)
        implements UserDetails, Serializable {

    public static CurrentUser of(User user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getPasswordHash());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
