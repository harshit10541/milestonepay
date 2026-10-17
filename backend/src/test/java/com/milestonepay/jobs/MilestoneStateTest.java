package com.milestonepay.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilestoneStateTest {

    @Test
    void namesAreTheAgreedOnes() {
        assertThat(Arrays.stream(MilestoneState.values()).map(MilestoneState::dbValue)).containsExactly(
                "draft", "criteria_approved", "funded", "submitted", "in_review",
                "awaiting_client", "changes_requested", "released", "cancelled", "disputed");
    }

    /** If someone adds a state to the enum but not to the migration (or the other way round), this fails. */
    @Test
    void matchesTheDatabaseCheckConstraint() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V2__core_schema.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher check = Pattern.compile("state\\s+text[^(]*CHECK \\(state IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(check.find()).as("milestones.state CHECK in V2").isTrue();
        List<String> inDb = Pattern.compile("'([a-z_]+)'").matcher(check.group(1)).results()
                .map(r -> r.group(1)).toList();
        assertThat(inDb).containsExactly(
                Arrays.stream(MilestoneState.values()).map(MilestoneState::dbValue).toArray(String[]::new));
    }

    @Test
    void convertsToAndFromTheDatabaseValue() {
        MilestoneStateConverter converter = new MilestoneStateConverter();
        for (MilestoneState s : MilestoneState.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(s))).isEqualTo(s);
        }
        assertThat(converter.convertToDatabaseColumn(MilestoneState.AWAITING_CLIENT)).isEqualTo("awaiting_client");
        assertThatThrownBy(() -> MilestoneState.fromDb("AWAITING_CLIENT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MilestoneState.fromDb("paid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonUsesTheAgreedNames() throws IOException {
        ObjectMapper json = new ObjectMapper();
        assertThat(json.writeValueAsString(MilestoneState.CHANGES_REQUESTED)).isEqualTo("\"changes_requested\"");
        assertThat(json.readValue("\"in_review\"", MilestoneState.class)).isEqualTo(MilestoneState.IN_REVIEW);
    }
}
