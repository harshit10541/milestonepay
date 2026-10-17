package com.milestonepay.auth;

import java.util.UUID;

/** What other modules and the API see of a user. */
public record UserView(UUID id, String email, String displayName) {
}
