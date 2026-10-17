package com.milestonepay.jobs;

import java.time.Instant;
import java.util.UUID;

/** One milestone as the API shows it. {@code yourRole} tells the screen which buttons to offer. */
public record MilestoneView(
        UUID id,
        UUID jobId,
        int position,
        String title,
        String brief,
        long amountMinor,
        String currency,
        MilestoneState state,
        PartyRole yourRole,
        int revisionRoundsAllowed,
        int revisionRoundsUsed,
        int reviewWindowSeconds,
        Instant clientCriteriaApprovedAt,
        Instant freelancerCriteriaApprovedAt,
        Instant reviewDeadlineAt,
        boolean needsClientDecision
) {

    static MilestoneView of(Milestone m, PartyRole role) {
        return new MilestoneView(m.id(), m.jobId(), m.position(), m.title(), m.brief(), m.amountMinor(),
                m.currency(), m.state(), role, m.revisionRoundsAllowed(), m.revisionRoundsUsed(),
                m.reviewWindowSeconds(), m.clientCriteriaApprovedAt(), m.freelancerCriteriaApprovedAt(),
                m.reviewDeadlineAt(), m.needsClientDecision());
    }
}
