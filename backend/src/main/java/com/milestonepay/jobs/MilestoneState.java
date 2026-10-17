package com.milestonepay.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The ten agreed milestone states. {@link #dbValue()} is the exact agreed name ({@code awaiting_client}),
 * used in the database and in JSON, so the API and the frontend see the same names as the docs.
 */
public enum MilestoneState {
    DRAFT,
    CRITERIA_APPROVED,
    FUNDED,
    SUBMITTED,
    IN_REVIEW,
    AWAITING_CLIENT,
    CHANGES_REQUESTED,
    RELEASED,
    CANCELLED,
    DISPUTED;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static MilestoneState fromDb(String value) {
        for (MilestoneState s : values()) {
            if (s.dbValue().equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown milestone state: " + value);
    }
}
