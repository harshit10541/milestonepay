package com.milestonepay.common;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorTest {

    @Test
    void formatsForTheEventsTable() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        assertThat(Actor.client(id).asText()).isEqualTo("client:00000000-0000-0000-0000-00000000000a");
        assertThat(Actor.freelancer(id).asText()).isEqualTo("freelancer:00000000-0000-0000-0000-00000000000a");
        assertThat(Actor.system("review-worker").asText()).isEqualTo("system:review-worker");
    }

    @Test
    void rejectsBadIds() {
        assertThatThrownBy(() -> Actor.system("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Actor.system("a b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(null, "x")).isInstanceOf(NullPointerException.class);
    }
}
