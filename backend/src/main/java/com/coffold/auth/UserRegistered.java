package com.coffold.auth;

/**
 * Published inside the registration transaction, so other modules can set a
 * new account up (default categories, a first wallet) without the auth code
 * knowing they exist. Listeners run in the same transaction: if one fails,
 * the account isn't created either.
 */
public record UserRegistered(Long userId) {
}
