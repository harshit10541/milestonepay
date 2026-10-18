package com.milestonepay.jobs;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Used when a new milestone doesn't set its own review window or revision rounds.
 * The real defaults are still undecided; application.yml has demo values (5 minutes, 2 rounds).
 */
@Validated
@ConfigurationProperties(prefix = "milestonepay.milestones")
public record MilestoneDefaults(
        @NotNull Duration reviewWindow,
        @Min(0) @Max(MilestoneDefaults.MAX_REVISION_ROUNDS) int revisionRounds
) {
    static final long MIN_REVIEW_WINDOW_SECONDS = 60;
    static final long MAX_REVIEW_WINDOW_SECONDS = 30L * 24 * 3600;
    static final int MAX_REVISION_ROUNDS = 10;
}
