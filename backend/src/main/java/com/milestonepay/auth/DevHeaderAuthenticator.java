package com.milestonepay.auth;

import com.milestonepay.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dev-only stand-in until the auth method is decided: the caller says who they are in an
 * {@code X-User-Id: <user uuid>} header. Anyone can claim to be anyone, so it only exists with the
 * {@code dev} profile; without it every endpoint that needs a caller answers 401.
 */
@Profile("dev")
@Component
class DevHeaderAuthenticator implements CallerAuthenticator {

    static final String HEADER = "X-User-Id";

    private final UserService users;

    DevHeaderAuthenticator(UserService users) {
        this.users = users;
    }

    @Override
    public UUID authenticate(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new UnauthorizedException("Missing " + HEADER + " header");
        }
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(HEADER + " must be a user id (UUID)");
        }
        if (!users.exists(id)) {
            throw new UnauthorizedException("Unknown user");
        }
        return id;
    }
}
