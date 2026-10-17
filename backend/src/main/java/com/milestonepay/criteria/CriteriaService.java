package com.milestonepay.criteria;

import com.milestonepay.ai.AiClient;
import com.milestonepay.ai.AiClientException;
import com.milestonepay.ai.CriteriaRequest;
import com.milestonepay.ai.CriteriaResponse;
import com.milestonepay.common.ConflictException;
import com.milestonepay.common.ForbiddenException;
import com.milestonepay.events.EventLog;
import com.milestonepay.jobs.MilestoneAccess;
import com.milestonepay.jobs.MilestoneService;
import com.milestonepay.jobs.MilestoneState;
import com.milestonepay.jobs.MilestoneView;
import com.milestonepay.jobs.PartyRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The agreed checklist for a milestone: generate it with the AI (contract call 1), then both sides approve it.
 * Milestone data (state, approvals) is changed only through {@link MilestoneService}.
 */
@Service
public class CriteriaService {

    static final String CRITERIA_GENERATED = "CRITERIA_GENERATED";

    static final int MAX_CRITERIA = 30;
    static final int MAX_TEXT = 1_000;
    static final int MAX_AI_ID = 64;
    static final int MAX_CHECK_METHOD = 100;

    private final CriterionRepository criteria;
    private final MilestoneService milestones;
    private final AiClient ai;
    private final EventLog eventLog;
    private final TransactionTemplate tx;
    private final Clock clock;

    CriteriaService(CriterionRepository criteria, MilestoneService milestones, AiClient ai, EventLog eventLog,
                    TransactionTemplate tx, Clock clock) {
        this.criteria = criteria;
        this.milestones = milestones;
        this.ai = ai;
        this.eventLog = eventLog;
        this.tx = tx;
        this.clock = clock;
    }

    /** The checklist in order. No ownership check: callers check access to the milestone first. */
    @Transactional(readOnly = true)
    public List<CriterionView> listForMilestone(UUID milestoneId) {
        return criteria.findByMilestoneIdOrderByPosition(milestoneId).stream().map(Criterion::toView).toList();
    }

    /**
     * Client only, draft only. Calls the AI (outside any transaction, since it can be slow), then replaces
     * any earlier checklist and clears both approvals in one transaction. Running it twice just gives a
     * fresh draft checklist; no money is involved.
     */
    public List<CriterionView> generate(UUID milestoneId, UUID callerId) {
        MilestoneAccess access = milestones.access(milestoneId, callerId);
        if (access.role() != PartyRole.CLIENT) {
            throw new ForbiddenException("Only the client can generate criteria");
        }
        requireDraft(access.state());

        CriteriaResponse response = ai.generateCriteria(
                new CriteriaRequest(access.jobId().toString(), milestoneId.toString(), access.brief()));
        List<CriteriaResponse.Criterion> checked = validate(response);

        return tx.execute(status -> {
            // Locks the milestone and re-checks it's still draft: someone may have approved meanwhile.
            milestones.clearCriteriaApprovals(milestoneId, callerId);
            criteria.deleteByMilestoneId(milestoneId);
            Instant now = clock.instant();
            List<Criterion> rows = new ArrayList<>();
            for (int i = 0; i < checked.size(); i++) {
                CriteriaResponse.Criterion c = checked.get(i);
                rows.add(new Criterion(milestoneId, i + 1, c.id(), c.text().trim(), CriterionType.parse(c.type()),
                        blankToNull(c.checkMethod()), now));
            }
            List<CriterionView> saved = criteria.saveAllAndFlush(rows).stream().map(Criterion::toView).toList();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", saved.size());
            data.put("objective", saved.stream().filter(c -> c.type() == CriterionType.OBJECTIVE).count());
            data.put("aiIds", saved.stream().map(CriterionView::aiId).toList());
            eventLog.record(milestoneId, CRITERIA_GENERATED, access.role().actor(callerId), data);
            return saved;
        });
    }

    /**
     * Either party, draft only, and only once there's a checklist. The second approval moves the milestone
     * to {@code criteria_approved}, which freezes the checklist.
     */
    @Transactional
    public MilestoneView approve(UUID milestoneId, UUID callerId) {
        MilestoneAccess access = milestones.lock(milestoneId, callerId);
        if (access.state() == MilestoneState.DRAFT && criteria.countByMilestoneId(milestoneId) == 0) {
            throw new ConflictException("There are no criteria to approve yet; generate them first");
        }
        return milestones.recordCriteriaApproval(milestoneId, callerId);
    }

    /** The AI's answer is untrusted input: check it against the contract before storing anything. */
    static List<CriteriaResponse.Criterion> validate(CriteriaResponse response) {
        if (response == null || response.criteria() == null || response.criteria().isEmpty()) {
            throw new AiClientException("AI returned no criteria");
        }
        if (response.criteria().size() > MAX_CRITERIA) {
            throw new AiClientException("AI returned more than " + MAX_CRITERIA + " criteria");
        }
        Set<String> ids = new HashSet<>();
        for (CriteriaResponse.Criterion c : response.criteria()) {
            if (c == null) {
                throw new AiClientException("AI returned an empty criterion");
            }
            if (c.id() == null || c.id().isBlank() || c.id().length() > MAX_AI_ID) {
                throw new AiClientException("AI criterion id is missing or too long");
            }
            if (!ids.add(c.id())) {
                throw new AiClientException("AI returned the criterion id " + c.id() + " twice");
            }
            if (c.text() == null || c.text().isBlank() || c.text().length() > MAX_TEXT) {
                throw new AiClientException("AI criterion " + c.id() + " has missing or too long text");
            }
            if (CriterionType.parse(c.type()) == null) {
                throw new AiClientException("AI criterion " + c.id() + " has type '" + c.type()
                        + "', expected objective or subjective");
            }
            if (c.checkMethod() != null && c.checkMethod().length() > MAX_CHECK_METHOD) {
                throw new AiClientException("AI criterion " + c.id() + " has a too long checkMethod");
            }
        }
        return response.criteria();
    }

    private static void requireDraft(MilestoneState state) {
        if (state != MilestoneState.DRAFT) {
            throw new ConflictException("Criteria can only change while the milestone is in draft (it is "
                    + state.dbValue() + ")");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
