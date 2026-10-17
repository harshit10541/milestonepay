package com.milestonepay.common;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Who caused something: a client, a freelancer, or a part of the system (worker, timer, webhook).
 * Stored in the events table as text, e.g. {@code client:3f2a...} or {@code system:review-worker}.
 */
public record Actor(Kind kind, String id) {

    public enum Kind { CLIENT, FREELANCER, SYSTEM }

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public Actor {
        Objects.requireNonNull(kind, "kind");
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Actor id must be 1-64 letters, digits, '-' or '_', got " + id);
        }
    }

    public static Actor client(UUID userId) {
        return new Actor(Kind.CLIENT, userId.toString());
    }

    public static Actor freelancer(UUID userId) {
        return new Actor(Kind.FREELANCER, userId.toString());
    }

    /** A named part of the backend, e.g. {@code system("review-worker")}. */
    public static Actor system(String name) {
        return new Actor(Kind.SYSTEM, name);
    }

    /** The form stored in {@code events.actor}. */
    public String asText() {
        return kind.name().toLowerCase() + ":" + id;
    }
}
