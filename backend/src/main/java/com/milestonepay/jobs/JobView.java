package com.milestonepay.jobs;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /jobs/{id}}: the job and a short line per milestone. */
public record JobView(UUID id, String title, UUID clientId, UUID freelancerId, PartyRole yourRole,
                      Instant createdAt, List<MilestoneSummary> milestones) {

    public record MilestoneSummary(UUID id, int position, String title, long amountMinor, String currency,
                                   MilestoneState state) {

        static MilestoneSummary of(Milestone m) {
            return new MilestoneSummary(m.id(), m.position(), m.title(), m.amountMinor(), m.currency(), m.state());
        }
    }
}
