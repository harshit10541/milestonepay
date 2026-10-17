package com.milestonepay.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "freelancer_id")
    private UUID freelancerId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Job() {
    }

    Job(UUID clientId, UUID freelancerId, String title, Instant createdAt) {
        this.clientId = clientId;
        this.freelancerId = freelancerId;
        this.title = title;
        this.createdAt = createdAt;
    }

    /** The caller's role on this job, or null if they're not a party to it. */
    PartyRole roleOf(UUID userId) {
        if (clientId.equals(userId)) {
            return PartyRole.CLIENT;
        }
        if (userId.equals(freelancerId)) {
            return PartyRole.FREELANCER;
        }
        return null;
    }

    UUID id() {
        return id;
    }

    UUID clientId() {
        return clientId;
    }

    UUID freelancerId() {
        return freelancerId;
    }

    String title() {
        return title;
    }

    Instant createdAt() {
        return createdAt;
    }
}
