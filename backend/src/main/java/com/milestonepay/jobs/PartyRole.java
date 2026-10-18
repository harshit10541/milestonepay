package com.milestonepay.jobs;

import com.fasterxml.jackson.annotation.JsonValue;
import com.milestonepay.common.Actor;

import java.util.Locale;
import java.util.UUID;

/** Which side of the job a user is on. */
public enum PartyRole {
    CLIENT,
    FREELANCER;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Actor actor(UUID userId) {
        return this == CLIENT ? Actor.client(userId) : Actor.freelancer(userId);
    }
}
