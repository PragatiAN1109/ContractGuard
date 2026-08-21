package com.example.claims.repair;

import com.example.claims.AssessmentOutcome;
import com.example.claims.ClaimAssessment;

/**
 * Generated against claim-v1. REPAIRABLE means "an assessor cleared this for repair", so it
 * immediately creates a repair plan and books parts.
 */
public class RepairWorkflowHandler {

    private final RepairPlanner planner;

    public RepairWorkflowHandler(RepairPlanner planner) {
        this.planner = planner;
    }

    public void handle(ClaimAssessment assessment) {
        switch (assessment.getOutcome()) {
            case REPAIRABLE -> createRepairPlan(assessment);
            case TOTAL_LOSS -> planner.settleTotalLoss(assessment.getClaimId());
            case REJECTED -> planner.closeClaim(assessment.getClaimId());
        }
    }

    private void createRepairPlan(ClaimAssessment assessment) {
        planner.createRepairPlan(assessment.getClaimId(), assessment.getEstimatedCostCents());
        planner.reserveParts(assessment.getClaimId());
    }

    public boolean isRepairable(ClaimAssessment assessment) {
        return assessment.getOutcome() == AssessmentOutcome.REPAIRABLE;
    }
}
