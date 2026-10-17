package com.milestonepay.jobs;

import com.milestonepay.common.Actor;
import com.milestonepay.events.EventLog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.milestonepay.jobs.MilestoneState.*;

/**
 * The one place that decides which milestone state changes are allowed. Every state change goes through
 * {@link #transition}, which refuses anything not in the map and writes a STATE_CHANGED event.
 * <p>
 * It only knows states, not who is calling: ownership checks ("only the client approves") stay in the
 * services and controllers, and money rules stay in the release engine.
 */
@Component
public class MilestoneStateMachine {

    static final String EVENT_TYPE = "STATE_CHANGED";

    private static final Map<MilestoneState, Set<MilestoneState>> ALLOWED = allowed();

    private static Map<MilestoneState, Set<MilestoneState>> allowed() {
        Map<MilestoneState, Set<MilestoneState>> m = new EnumMap<>(MilestoneState.class);
        m.put(DRAFT, EnumSet.of(CRITERIA_APPROVED, CANCELLED));          // both approve checklist; client cancels
        m.put(CRITERIA_APPROVED, EnumSet.of(FUNDED, CANCELLED));         // PayPal authorization; client cancels
        m.put(FUNDED, EnumSet.of(SUBMITTED, CANCELLED));                 // freelancer submits; cancel voids the hold
        m.put(SUBMITTED, EnumSet.of(IN_REVIEW));                         // review worker claims the job
        m.put(IN_REVIEW, EnumSet.of(AWAITING_CLIENT));                   // report saved, or review failed
        m.put(AWAITING_CLIENT, EnumSet.of(RELEASED, CHANGES_REQUESTED, DISPUTED));
        m.put(CHANGES_REQUESTED, EnumSet.of(SUBMITTED));                 // freelancer resubmits
        m.put(RELEASED, EnumSet.noneOf(MilestoneState.class));
        m.put(CANCELLED, EnumSet.noneOf(MilestoneState.class));
        m.put(DISPUTED, EnumSet.noneOf(MilestoneState.class));           // resolution is still open
        m.replaceAll((from, to) -> Collections.unmodifiableSet(to));
        return Collections.unmodifiableMap(m);
    }

    private final EventLog eventLog;

    MilestoneStateMachine(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    public boolean canTransition(MilestoneState from, MilestoneState to) {
        return ALLOWED.get(Objects.requireNonNull(from, "from")).contains(Objects.requireNonNull(to, "to"));
    }

    /** The states a milestone in {@code from} may move to, e.g. for the UI to show which buttons apply. */
    public Set<MilestoneState> nextStates(MilestoneState from) {
        return ALLOWED.get(Objects.requireNonNull(from, "from"));
    }

    /**
     * Checks the change and records it. Returns {@code to}, which the caller stores on the milestone in the
     * same transaction. MANDATORY: calling this outside a transaction fails, so the state and its event
     * always commit together.
     *
     * @throws IllegalTransitionException if the map does not allow {@code from -> to}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MilestoneState transition(UUID milestoneId, MilestoneState from, MilestoneState to,
                                     Actor actor, String reason) {
        Objects.requireNonNull(milestoneId, "milestoneId");
        Objects.requireNonNull(actor, "actor");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for every state change");
        }
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
        eventLog.record(milestoneId, EVENT_TYPE, actor,
                Map.of("from", from.dbValue(), "to", to.dbValue(), "reason", reason));
        return to;
    }
}
