package com.milestonepay.jobs;

import com.milestonepay.auth.CurrentUser;
import com.milestonepay.criteria.CriteriaService;
import com.milestonepay.criteria.CriterionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /milestones/{id}} is the screen's one call for a milestone, so it gathers data from other
 * modules through their services: criteria now, the latest AI report and payment status once those exist.
 */
@RestController
@RequestMapping("/milestones")
class MilestoneController {

    private final MilestoneService milestones;
    private final CriteriaService criteria;

    MilestoneController(MilestoneService milestones, CriteriaService criteria) {
        this.milestones = milestones;
        this.criteria = criteria;
    }

    record MilestoneDetail(MilestoneView milestone, List<CriterionView> criteria) {
    }

    /** Client or freelancer of the job; anyone else gets 404. */
    @GetMapping("/{milestoneId}")
    MilestoneDetail get(@PathVariable UUID milestoneId, CurrentUser caller) {
        MilestoneView milestone = milestones.get(milestoneId, caller.id()); // ownership check first
        return new MilestoneDetail(milestone, criteria.listForMilestone(milestoneId));
    }
}
