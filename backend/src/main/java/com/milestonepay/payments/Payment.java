package com.milestonepay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One funding attempt for a milestone: the PayPal order and, once approved, its authorization (hold). */
@Entity
@Table(name = "payments")
class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "milestone_id", nullable = false, updatable = false)
    private UUID milestoneId;

    @Column(nullable = false, updatable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "paypal_order_id", nullable = false, unique = true, updatable = false)
    private String paypalOrderId;

    @Column(name = "paypal_authorization_id", unique = true)
    private String paypalAuthorizationId;

    @Column(name = "paypal_capture_id", unique = true)
    private String paypalCaptureId;

    @Column(name = "authorization_expires_at")
    private Instant authorizationExpiresAt;

    @Column(name = "approve_url")
    private String approveUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    Payment(UUID milestoneId, int attempt, long amountMinor, String currency, String paypalOrderId,
            String approveUrl, Instant now) {
        this.milestoneId = milestoneId;
        this.attempt = attempt;
        this.status = PaymentStatus.ORDER_CREATED;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.paypalOrderId = paypalOrderId;
        this.approveUrl = approveUrl;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void authorized(String authorizationId, Instant expiresAt, Instant now) {
        this.status = PaymentStatus.AUTHORIZED;
        this.paypalAuthorizationId = authorizationId;
        this.authorizationExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    void failed(Instant now) {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    UUID milestoneId() {
        return milestoneId;
    }

    int attempt() {
        return attempt;
    }

    PaymentStatus status() {
        return status;
    }

    String paypalOrderId() {
        return paypalOrderId;
    }

    PaymentView toView() {
        return new PaymentView(attempt, status, amountMinor, currency, paypalOrderId, approveUrl,
                paypalAuthorizationId, authorizationExpiresAt, createdAt, updatedAt);
    }
}
