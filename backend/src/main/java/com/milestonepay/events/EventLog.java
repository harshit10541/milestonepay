package com.milestonepay.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milestonepay.common.Actor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The events module's public API: appends one row to the audit trail.
 * <p>
 * There is deliberately no update or delete method, and the database rejects both with a trigger (V2).
 * Call it inside the same transaction as the change it records, so both commit or neither does.
 */
@Service
public class EventLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    EventLog(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param milestoneId null for events not tied to one milestone
     * @param type        upper-case event name, e.g. {@code STATE_CHANGED}
     */
    public void record(UUID milestoneId, String type, Actor actor, Map<String, ?> data) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Event type is required");
        }
        Objects.requireNonNull(actor, "actor");
        jdbc.update("INSERT INTO events (milestone_id, type, actor, data, at) VALUES (?, ?, ?, ?::jsonb, ?)",
                milestoneId, type, actor.asText(), toJson(data == null ? Map.of() : data),
                Timestamp.from(clock.instant()));
    }

    private String toJson(Map<String, ?> data) {
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event data is not serializable to JSON", e);
        }
    }
}
