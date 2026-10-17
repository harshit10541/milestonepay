package com.milestonepay.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A milestone row. {@code state} has no public setter: it changes only in {@link MilestoneService}, after
 * {@link MilestoneStateMachine#transition} has allowed the change and logged it.
 */
@Entity
@Table(name = "milestones")
class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String brief;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private MilestoneState state;

    @Column(name = "revision_rounds_allowed", nullable = false)
    private int revisionRoundsAllowed;

    @Column(name = "revision_rounds_used", nullable = false)
    private int revisionRoundsUsed;

    @Column(name = "review_window_seconds", nullable = false)
    private int reviewWindowSeconds;

    @Column(name = "client_criteria_approved_at")
    private Instant clientCriteriaApprovedAt;

    @Column(name = "freelancer_criteria_approved_at")
    private Instant freelancerCriteriaApprovedAt;

    @Column(name = "review_deadline_at")
    private Instant reviewDeadlineAt;

    @Column(name = "needs_client_decision", nullable = false)
    private boolean needsClientDecision;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Milestone() {
    }

    Milestone(UUID jobId, int position, String title, String brief, long amountMinor, String currency,
              int revisionRoundsAllowed, int reviewWindowSeconds, Instant now) {
        this.jobId = jobId;
        this.position = position;
        this.title = title;
        this.brief = brief;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.state = MilestoneState.DRAFT;
        this.revisionRoundsAllowed = revisionRoundsAllowed;
        this.reviewWindowSeconds = reviewWindowSeconds;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Only call with the value {@link MilestoneStateMachine#transition} returned. */
    void moveTo(MilestoneState allowed, Instant now) {
        this.state = allowed;
        this.updatedAt = now;
    }

    /** @return true if this call added the approval, false if that party had already approved */
    boolean approveCriteria(PartyRole by, Instant now) {
        if (by == PartyRole.CLIENT) {
            if (clientCriteriaApprovedAt != null) {
                return false;
            }
            clientCriteriaApprovedAt = now;
        } else {
            if (freelancerCriteriaApprovedAt != null) {
                return false;
            }
            freelancerCriteriaApprovedAt = now;
        }
        updatedAt = now;
        return true;
    }

    boolean bothApprovedCriteria() {
        return clientCriteriaApprovedAt != null && freelancerCriteriaApprovedAt != null;
    }

    /** The checklist changed, so earlier approvals no longer count. @return true if any were cleared */
    boolean clearCriteriaApprovals(Instant now) {
        boolean had = clientCriteriaApprovedAt != null || freelancerCriteriaApprovedAt != null;
        clientCriteriaApprovedAt = null;
        freelancerCriteriaApprovedAt = null;
        if (had) {
            updatedAt = now;
        }
        return had;
    }

    UUID id() {
        return id;
    }

    UUID jobId() {
        return jobId;
    }

    int position() {
        return position;
    }

    String title() {
        return title;
    }

    String brief() {
        return brief;
    }

    long amountMinor() {
        return amountMinor;
    }

    String currency() {
        return currency;
    }

    MilestoneState state() {
        return state;
    }

    int revisionRoundsAllowed() {
        return revisionRoundsAllowed;
    }

    int revisionRoundsUsed() {
        return revisionRoundsUsed;
    }

    int reviewWindowSeconds() {
        return reviewWindowSeconds;
    }

    Instant clientCriteriaApprovedAt() {
        return clientCriteriaApprovedAt;
    }

    Instant freelancerCriteriaApprovedAt() {
        return freelancerCriteriaApprovedAt;
    }

    Instant reviewDeadlineAt() {
        return reviewDeadlineAt;
    }

    boolean needsClientDecision() {
        return needsClientDecision;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
