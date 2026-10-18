package com.milestonepay.auth;

import com.milestonepay.common.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The auth module's public API for other modules. */
@Service
public class UserService {

    private final UserRepository users;
    private final Clock clock;

    UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return userId != null && users.existsById(userId);
    }

    @Transactional
    public UserView create(String email, String displayName) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("A user with that email already exists");
        }
        return users.save(new User(normalized, displayName.trim(), clock.instant())).toView();
    }

    @Transactional(readOnly = true)
    List<UserView> all() {
        return users.findAll().stream().map(User::toView).toList();
    }
}
