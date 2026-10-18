package com.milestonepay.criteria;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Objective: the AI can check it. Subjective: only the client judges it. */
public enum CriterionType {
    OBJECTIVE,
    SUBJECTIVE;

    @JsonValue
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return null for anything other than the two agreed values */
    static CriterionType parse(String value) {
        for (CriterionType t : values()) {
            if (t.dbValue().equals(value)) {
                return t;
            }
        }
        return null;
    }
}
