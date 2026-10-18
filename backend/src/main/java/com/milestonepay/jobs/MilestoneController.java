package com.milestonepay.jobs;

import com.milestonepay.auth.CurrentUser;
import com.milestonepay.criteria.CriteriaService;
import com.milestonepay.criteria.CriterionView;
import com.milestonepay.payments.FundingService;
import com.milestonepay.payments.PaymentView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /milestones/{id}} is the screen's one call for a milestone, so it gathers data from other
 * modules through their services: criteria, the latest funding attempt, and later the latest AI report.
 */
@RestController
@RequestMapping("/milestones")
class MilestoneController {

    private final MilestoneService milestones;
    private final CriteriaService criteria;
    private final FundingService funding;

    MilestoneController(MilestoneService milestones, CriteriaService criteria, FundingService funding) {
        this.milestones = milestones;
        this.criteria = criteria;
        this.funding = funding;
    }

    /** {@code payment} is the latest funding attempt, or null before the client presses Fund. */
    record MilestoneDetail(MilestoneView milestone, List<CriterionView> criteria, PaymentView payment) {
    }

    /** Client or freelancer of the job; anyone else gets 404. */
    @GetMapping("/{milestoneId}")
    MilestoneDetail get(@PathVariable UUID milestoneId, CurrentUser caller) {
        MilestoneView milestone = milestones.get(milestoneId, caller.id()); // ownership check first
        return new MilestoneDetail(milestone, criteria.listForMilestone(milestoneId),
                funding.latest(milestoneId).orElse(null));
    }
}
