package com.milestonepay.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dev-only: create the client and freelancer users whose ids go in {@code X-User-Id}.
 * Goes away (or behind admin auth) once the auth method is decided.
 */
@Profile("dev")
@RestController
@RequestMapping("/dev/users")
class DevUserController {

    private final UserService users;

    DevUserController(UserService users) {
        this.users = users;
    }

    record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                             @NotBlank @Size(max = 100) String displayName) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserView create(@Valid @RequestBody CreateUserRequest req) {
        return users.create(req.email(), req.displayName());
    }

    @GetMapping
    List<UserView> list() {
        return users.all();
    }
}
