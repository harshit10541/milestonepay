package com.milestonepay.criteria;

import com.milestonepay.auth.CurrentUser;
import com.milestonepay.jobs.MilestoneView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/milestones/{milestoneId}/criteria")
class CriteriaController {

    private final CriteriaService criteria;

    CriteriaController(CriteriaService criteria) {
        this.criteria = criteria;
    }

    record CriteriaList(UUID milestoneId, List<CriterionView> criteria) {
    }

    record ApprovalResult(MilestoneView milestone, List<CriterionView> criteria) {
    }

    /** Client only, milestone in draft. Replaces any earlier checklist and clears both approvals. */
    @PostMapping("/generate")
    CriteriaList generate(@PathVariable UUID milestoneId, CurrentUser caller) {
        return new CriteriaList(milestoneId, criteria.generate(milestoneId, caller.id()));
    }

    /** Client or freelancer, milestone in draft. The second approval moves it to criteria_approved. */
    @PostMapping("/approve")
    ApprovalResult approve(@PathVariable UUID milestoneId, CurrentUser caller) {
        MilestoneView milestone = criteria.approve(milestoneId, caller.id());
        return new ApprovalResult(milestone, criteria.listForMilestone(milestoneId));
    }
}
