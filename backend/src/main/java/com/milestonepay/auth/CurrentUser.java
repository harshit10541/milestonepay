package com.milestonepay.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated caller. Add it as a controller method parameter and it's filled in for you;
 * a request without a valid caller never reaches the controller (401).
 */
public record CurrentUser(UUID id) {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
    }
}
