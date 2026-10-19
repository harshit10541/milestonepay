package com.milestonepay.payments;

import com.milestonepay.common.Actor;
import com.milestonepay.common.ConflictException;
import com.milestonepay.common.ForbiddenException;
import com.milestonepay.common.Money;
import com.milestonepay.common.NotFoundException;
import com.milestonepay.events.EventLog;
import com.milestonepay.jobs.MilestoneAccess;
import com.milestonepay.jobs.MilestoneService;
import com.milestonepay.jobs.MilestoneState;
import com.milestonepay.jobs.MilestoneView;
import com.milestonepay.jobs.PartyRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Funding a milestone, stored in the payments table:
 * <ol>
 *   <li>{@link #fund}: the client asks to fund; we create a PayPal order (intent AUTHORIZE) and return its
 *       approve link. Nothing is held yet.</li>
 *   <li>The client approves on PayPal, which sends their browser back with the order id.</li>
 *   <li>{@link #completeFunding}: we authorize the order (PayPal now holds the money), store the
 *       authorization and move the milestone {@code criteria_approved -> funded}.</li>
 * </ol>
 * PayPal calls happen outside database transactions (they can be slow), and every one uses a stable
 * {@code PayPal-Request-Id}, so a retry after a crash gets PayPal's first answer back.
 */
@Service
public class FundingService {

    private static final Logger log = LoggerFactory.getLogger(FundingService.class);

    static final String PAYMENT_ORDER_CREATED = "PAYMENT_ORDER_CREATED";
    static final String PAYMENT_ABANDONED = "PAYMENT_ABANDONED";
    static final String PAYMENT_AUTHORIZED = "PAYMENT_AUTHORIZED";

    /** Order statuses where the existing order can still be approved, or already was. */
    private static final Set<String> ORDER_STILL_USABLE = Set.of("CREATED", "SAVED", "PAYER_ACTION_REQUIRED", "APPROVED");
    /** PayPal's status for an authorization that is holding money. */
    private static final String HELD = "CREATED";
    private static final Pattern ORDER_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final PaymentRepository payments;
    private final PaymentService paypal;
    private final PayPalProperties props;
    private final MilestoneService milestones;
    private final EventLog eventLog;
    private final TransactionTemplate tx;
    private final Clock clock;

    FundingService(PaymentRepository payments, PaymentService paypal, PayPalProperties props,
                   MilestoneService milestones, EventLog eventLog, TransactionTemplate tx, Clock clock) {
        this.payments = payments;
        this.paypal = paypal;
        this.props = props;
        this.milestones = milestones;
        this.eventLog = eventLog;
        this.tx = tx;
        this.clock = clock;
    }

    /** The latest funding attempt, for {@code GET /milestones/{id}}. No ownership check: callers do that first. */
    @Transactional(readOnly = true)
    public Optional<PaymentView> latest(UUID milestoneId) {
        return payments.findTopByMilestoneIdOrderByAttemptDesc(milestoneId).map(Payment::toView);
    }

    /**
     * Client only, {@code criteria_approved} only. Returns the attempt with its approve link. Pressing it twice
     * returns the same order while it can still be approved; a new attempt starts only when the old order
     * is dead (e.g. expired unapproved), so the client is never stuck.
     */
    public PaymentView fund(UUID milestoneId, UUID callerId) {
        MilestoneAccess access = milestones.access(milestoneId, callerId);
        if (access.role() != PartyRole.CLIENT) {
            throw new ForbiddenException("Only the client can fund a milestone");
        }
        if (access.state() != MilestoneState.CRITERIA_APPROVED) {
            throw new ConflictException("A milestone can be funded once both sides approved the criteria (it is "
                    + access.state().dbValue() + ")");
        }
        Actor actor = Actor.client(callerId);

        int attempt = 1;
        Optional<Payment> last = payments.findTopByMilestoneIdOrderByAttemptDesc(milestoneId);
        if (last.isPresent()) {
            Payment p = last.get();
            switch (p.status()) {
                case ORDER_CREATED -> {
                    String orderStatus = orderStatusOrNull(p.paypalOrderId());
                    if (orderStatus != null && ORDER_STILL_USABLE.contains(orderStatus)) {
                        return p.toView();
                    }
                    abandon(p.id(), orderStatus, actor);
                }
                case AUTHORIZED, CAPTURED -> throw new ConflictException("This milestone is already funded");
                case VOIDED, EXPIRED, FAILED -> { }
            }
            attempt = p.attempt() + 1;
        }
        if (attempt > PaymentService.MAX_ATTEMPT) {
            throw new ConflictException("Too many funding attempts for this milestone");
        }

        CreatedOrder order = paypal.createAuthorizeOrder(milestoneId.toString(), attempt, access.amount(),
                access.title(), props.milestoneReturnUrl(), props.milestoneCancelUrl());
        int thisAttempt = attempt;
        try {
            return tx.execute(status -> saveOrder(milestoneId, thisAttempt, access.amount(), order, actor));
        } catch (DataIntegrityViolationException e) {
            // Another request saved this attempt first (double click). PayPal gave both the same order,
            // because the request id is the same, so return the saved row.
            return payments.findByMilestoneIdAndAttempt(milestoneId, thisAttempt).map(Payment::toView)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * PayPal sent the client back after approving. Places the hold and marks the milestone funded.
     * Calling it again for the same order returns the same result.
     */
    public FundingResult completeFunding(String orderId, UUID callerId) {
        if (orderId == null || !ORDER_ID.matcher(orderId).matches()) {
            throw new IllegalArgumentException("orderId must be 1-64 letters, digits or dashes");
        }
        Payment payment = payments.findByPaypalOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("No funding order with that id"));
        UUID milestoneId = payment.milestoneId();
        MilestoneAccess access = milestones.access(milestoneId, callerId); // 404 for outsiders
        if (access.role() != PartyRole.CLIENT) {
            throw new ForbiddenException("Only the client can fund a milestone");
        }
        if (payment.status() == PaymentStatus.AUTHORIZED) {
            return new FundingResult(milestones.get(milestoneId, callerId), payment.toView());
        }
        if (payment.status() != PaymentStatus.ORDER_CREATED) {
            throw new ConflictException("This PayPal order can't be used any more (" + payment.status()
                    + "); fund the milestone again");
        }
        if (access.state() != MilestoneState.CRITERIA_APPROVED) {
            throw new ConflictException("The milestone can't be funded now (it is " + access.state().dbValue() + ")");
        }

        AuthorizationResult auth;
        try {
            auth = paypal.authorizeOrder(milestoneId.toString(), payment.attempt(), orderId);
        } catch (PayPalException e) {
            if ("ORDER_NOT_APPROVED".equals(e.issue())) {
                throw new ConflictException("The order hasn't been approved on PayPal yet");
            }
            throw e;
        }
        if (!HELD.equals(auth.status())) {
            throw new PayPalException(200, "UNEXPECTED_AUTHORIZATION_STATUS", null,
                    "authorization " + auth.authorizationId() + " is " + auth.status() + ", not holding money");
        }
        if (!access.amount().equals(auth.amount())) {
            // Never mark funded for a different amount than agreed; release the wrong hold straight away.
            log.error("PayPal held {} for milestone {} but the milestone is {}; voiding",
                    auth.amount(), milestoneId, access.amount());
            paypal.voidAuthorization(milestoneId.toString(), payment.attempt(), auth.authorizationId());
            throw new PayPalException(200, "AMOUNT_MISMATCH", null, "held amount differs from the milestone amount");
        }

        return tx.execute(status -> {
            Payment locked = payments.findByIdForUpdate(payment.id()).orElseThrow();
            if (locked.status() == PaymentStatus.AUTHORIZED) { // a parallel call finished first
                return new FundingResult(milestones.get(milestoneId, callerId), locked.toView());
            }
            locked.authorized(auth.authorizationId(), auth.expiresAt(), clock.instant());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("attempt", locked.attempt());
            data.put("paypalOrderId", orderId);
            data.put("authorizationId", auth.authorizationId());
            data.put("amountMinor", auth.amount().minor());
            data.put("currency", auth.amount().currency());
            data.put("expiresAt", auth.expiresAt() == null ? null : auth.expiresAt().toString());
            eventLog.record(milestoneId, PAYMENT_AUTHORIZED, Actor.client(callerId), data);
            MilestoneView funded = milestones.markFunded(milestoneId, callerId, auth.authorizationId());
            return new FundingResult(funded, locked.toView());
        });
    }

    private PaymentView saveOrder(UUID milestoneId, int attempt, Money amount, CreatedOrder order, Actor actor) {
        Optional<Payment> existing = payments.findByMilestoneIdAndAttempt(milestoneId, attempt);
        if (existing.isPresent()) { // retry after a crash, or a parallel request
            return existing.get().toView();
        }
        Payment saved = payments.saveAndFlush(new Payment(milestoneId, attempt, amount.minor(), amount.currency(),
                order.orderId(), order.approveUrl(), clock.instant()));
        eventLog.record(milestoneId, PAYMENT_ORDER_CREATED, actor, Map.of(
                "attempt", attempt, "paypalOrderId", order.orderId(),
                "amountMinor", amount.minor(), "currency", amount.currency()));
        return saved.toView();
    }

    private void abandon(UUID paymentId, String paypalStatus, Actor actor) {
        tx.executeWithoutResult(status -> {
            Payment p = payments.findByIdForUpdate(paymentId).orElseThrow();
            if (p.status() != PaymentStatus.ORDER_CREATED) {
                return;
            }
            p.failed(clock.instant());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("attempt", p.attempt());
            data.put("paypalOrderId", p.paypalOrderId());
            data.put("paypalStatus", paypalStatus == null ? "NOT_FOUND" : paypalStatus);
            eventLog.record(p.milestoneId(), PAYMENT_ABANDONED, actor, data);
        });
    }

    /** null when PayPal no longer knows the order (404). */
    private String orderStatusOrNull(String orderId) {
        try {
            return paypal.getOrderStatus(orderId);
        } catch (PayPalException e) {
            if (e.httpStatus() == 404) {
                return null;
            }
            throw e;
        }
    }

    /** {@code POST /payments/paypal/return}: the milestone (now funded) and the payment that funded it. */
    public record FundingResult(MilestoneView milestone, PaymentView payment) {
    }
}
