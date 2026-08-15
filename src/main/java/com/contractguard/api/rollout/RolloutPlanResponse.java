package com.contractguard.api.rollout;

import com.contractguard.rollout.RolloutPlan;

import java.util.List;
import java.util.UUID;

/** Guidance derived from a persisted analysis. Carries no combined safe/unsafe verdict. */
public record RolloutPlanResponse(UUID analysisId,
                                  String strategy,
                                  String summary,
                                  List<RolloutStepResponse> steps,
                                  List<String> limitations) {

    public static RolloutPlanResponse from(UUID analysisId, RolloutPlan plan) {
        return new RolloutPlanResponse(
                analysisId,
                plan.strategy().name(),
                plan.summary(),
                plan.steps().stream().map(RolloutStepResponse::from).toList(),
                plan.limitations());
    }
}
