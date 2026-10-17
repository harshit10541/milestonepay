package com.milestonepay.jobs;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /jobs} body. The caller becomes the client.
 * {@code reviewWindowSeconds} and {@code revisionRoundsAllowed} are optional; when left out the
 * configured defaults ({@link MilestoneDefaults}) apply.
 */
public record CreateJobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull UUID freelancerId,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull NewMilestone> milestones
) {

    public record NewMilestone(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10_000) String brief,
            /* Minor units: 30000 = 300.00 USD. The cap only guards against typos and overflow. */
            @NotNull @Positive @Max(1_000_000_000L) Long amountMinor,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter upper-case ISO code") String currency,
            @Min(MilestoneDefaults.MIN_REVIEW_WINDOW_SECONDS) @Max(MilestoneDefaults.MAX_REVIEW_WINDOW_SECONDS)
            Integer reviewWindowSeconds,
            @Min(0) @Max(MilestoneDefaults.MAX_REVISION_ROUNDS) Integer revisionRoundsAllowed
    ) {
    }
}
