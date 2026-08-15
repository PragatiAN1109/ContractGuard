package com.contractguard.api.rollout;

import com.contractguard.rollout.RolloutStep;

public record RolloutStepResponse(int order, String action, String target, String reason) {

    static RolloutStepResponse from(RolloutStep step) {
        return new RolloutStepResponse(step.order(), step.action().name(), step.target(), step.reason());
    }
}
