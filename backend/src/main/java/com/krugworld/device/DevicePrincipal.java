package com.krugworld.device;

import java.io.Serializable;

/**
 * Who is calling on a {@code /api/device/**} request: a paired device, acting
 * for {@link #userId()}. Deliberately not a {@code CurrentUser}, so a device
 * token can never reach the endpoints that take one (password, preferences,
 * deleting things in bulk...) — only the handful built for devices.
 */
public record DevicePrincipal(Long deviceId, Long userId, String name) implements Serializable {
}
