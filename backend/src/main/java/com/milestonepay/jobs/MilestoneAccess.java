package com.milestonepay.jobs;

import com.milestonepay.common.Money;

import java.util.UUID;

/**
 * What another module learns after {@link MilestoneService} has checked the caller is a party to the
 * milestone's job: its ids, current state, the caller's role, the brief and the amount.
 */
public record MilestoneAccess(UUID milestoneId, UUID jobId, MilestoneState state, PartyRole role, String title,
                              String brief, Money amount) {
}
