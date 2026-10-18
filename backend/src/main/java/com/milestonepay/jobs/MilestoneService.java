package com.milestonepay.jobs;

import com.milestonepay.common.Actor;
import com.milestonepay.common.ConflictException;
import com.milestonepay.common.ForbiddenException;
import com.milestonepay.common.Money;
import com.milestonepay.common.NotFoundException;
import com.milestonepay.events.EventLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The jobs module's public API for milestones. Other modules (criteria now; submissions and release later)
 * go through here instead of touching the milestones table, and every state change here goes through
 * {@link MilestoneStateMachine#transition}.
 * <p>
 * Every method takes the caller's id and answers 404 when the caller isn't a party to the job, so outsiders
 * can't probe which ids exist. Role rules ("only the client may ...") are the caller module's job, using the
 * role in {@link MilestoneAccess}.
 */
@Service
public class MilestoneService {

    static final String CRITERIA_APPROVED = "CRITERIA_APPROVED";
    static final String CRITERIA_APPROVALS_CLEARED = "CRITERIA_APPROVALS_CLEARED";

    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final MilestoneStateMachine stateMachine;
    private final EventLog eventLog;
    private final Clock clock;

    MilestoneService(MilestoneRepository milestones, JobRepository jobs, MilestoneStateMachine stateMachine,
                     EventLog eventLog, Clock clock) {
        this.milestones = milestones;
        this.jobs = jobs;
        this.stateMachine = stateMachine;
        this.eventLog = eventLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MilestoneView get(UUID milestoneId, UUID callerId) {
        Milestone m = milestones.findById(milestoneId).orElseThrow(MilestoneService::notFound);
        return MilestoneView.of(m, roleOrNotFound(m, callerId));
    }

    /** Checks the caller is a party and says what role they have. No lock: use for checks before slow work. */
    @Transactional(readOnly = true)
    public MilestoneAccess access(UUID milestoneId, UUID callerId) {
        Milestone m = milestones.findById(milestoneId).orElseThrow(MilestoneService::notFound);
        return access(m, roleOrNotFound(m, callerId));
    }

    /**
     * Same as {@link #access} but locks the milestone row until the surrounding transaction ends, so the
     * caller can change related data (e.g. criteria) without racing another request on the same milestone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MilestoneAccess lock(UUID milestoneId, UUID callerId) {
        Milestone m = lockedOrNotFound(milestoneId);
        return access(m, roleOrNotFound(m, callerId));
    }

    /**
     * The checklist is about to change, so earlier approvals no longer count. Only allowed in {@code draft}:
     * once both sides approved, the criteria are frozen.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearCriteriaApprovals(UUID milestoneId, UUID callerId) {
        Milestone m = lockedOrNotFound(milestoneId);
        PartyRole role = roleOrNotFound(m, callerId);
        requireDraft(m, "Criteria can only change while the milestone is in draft");
        if (m.clearCriteriaApprovals(clock.instant())) {
            eventLog.record(m.id(), CRITERIA_APPROVALS_CLEARED, role.actor(callerId), Map.of());
        }
    }

    /**
     * Records the caller's approval of the current criteria. The second approval (client and freelancer)
     * moves the milestone to {@code criteria_approved}. Approving twice is harmless and changes nothing.
     * The caller (criteria module) must check that criteria exist first.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MilestoneView recordCriteriaApproval(UUID milestoneId, UUID callerId) {
        Milestone m = lockedOrNotFound(milestoneId);
        PartyRole role = roleOrNotFound(m, callerId);
        boolean alreadyApproved = role == PartyRole.CLIENT
                ? m.clientCriteriaApprovedAt() != null
                : m.freelancerCriteriaApprovedAt() != null;
        if (alreadyApproved) {
            return MilestoneView.of(m, role);
        }
        requireDraft(m, "Criteria can only be approved while the milestone is in draft");

        Instant now = clock.instant();
        Actor actor = role.actor(callerId);
        m.approveCriteria(role, now);
        eventLog.record(m.id(), CRITERIA_APPROVED, actor, Map.of("by", role.jsonValue()));
        if (m.bothApprovedCriteria()) {
            m.moveTo(stateMachine.transition(m.id(), m.state(), MilestoneState.CRITERIA_APPROVED, actor,
                    "client and freelancer approved the criteria"), now);
        }
        return MilestoneView.of(m, role);
    }

    /**
     * PayPal has placed the hold: {@code criteria_approved -> funded}. Called by the payments module in the
     * same transaction that stores the authorization, so the payment row and the state change commit together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MilestoneView markFunded(UUID milestoneId, UUID callerId, String authorizationId) {
        Milestone m = lockedOrNotFound(milestoneId);
        PartyRole role = roleOrNotFound(m, callerId);
        if (role != PartyRole.CLIENT) {
            throw new ForbiddenException("Only the client can fund a milestone");
        }
        m.moveTo(stateMachine.transition(m.id(), m.state(), MilestoneState.FUNDED, role.actor(callerId),
                "PayPal authorization " + authorizationId + " placed"), clock.instant());
        return MilestoneView.of(m, role);
    }

    private Milestone lockedOrNotFound(UUID milestoneId) {
        return milestones.findByIdForUpdate(milestoneId).orElseThrow(MilestoneService::notFound);
    }

    private PartyRole roleOrNotFound(Milestone m, UUID callerId) {
        PartyRole role = jobs.findById(m.jobId()).map(job -> job.roleOf(callerId)).orElse(null);
        if (role == null) {
            throw notFound();
        }
        return role;
    }

    private static void requireDraft(Milestone m, String message) {
        if (m.state() != MilestoneState.DRAFT) {
            throw new ConflictException(message + " (it is " + m.state().dbValue() + ")");
        }
    }

    private static MilestoneAccess access(Milestone m, PartyRole role) {
        return new MilestoneAccess(m.id(), m.jobId(), m.state(), role, m.title(), m.brief(),
                Money.of(m.amountMinor(), m.currency()));
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Milestone not found");
    }
}
