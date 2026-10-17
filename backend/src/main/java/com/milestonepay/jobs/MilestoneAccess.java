package com.milestonepay.jobs;

import java.util.UUID;

/**
 * What another module learns after {@link MilestoneService} has checked the caller is a party to the
 * milestone's job: its ids, current state, the caller's role and the brief.
 */
public record MilestoneAccess(UUID milestoneId, UUID jobId, MilestoneState state, PartyRole role, String brief) {
}
