package com.milestonepay.criteria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "criteria")
class Criterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "milestone_id", nullable = false, updatable = false)
    private UUID milestoneId;

    @Column(nullable = false)
    private int position;

    /** The AI's own id from contract call 1 (e.g. "c1"), so call 2's criterionId maps back. */
    @Column(name = "ai_id")
    private String aiId;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private CriterionType type;

    @Column(name = "check_method")
    private String checkMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Criterion() {
    }

    Criterion(UUID milestoneId, int position, String aiId, String text, CriterionType type, String checkMethod,
              Instant createdAt) {
        this.milestoneId = milestoneId;
        this.position = position;
        this.aiId = aiId;
        this.text = text;
        this.type = type;
        this.checkMethod = checkMethod;
        this.createdAt = createdAt;
    }

    CriterionView toView() {
        return new CriterionView(id, position, aiId, text, type, checkMethod);
    }
}
