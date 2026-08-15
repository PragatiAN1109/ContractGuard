package com.contractguard.api.rollout;

import com.contractguard.rollout.RolloutPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RolloutController {

    private final RolloutPlanService rolloutPlanService;

    public RolloutController(RolloutPlanService rolloutPlanService) {
        this.rolloutPlanService = rolloutPlanService;
    }

    @GetMapping("/api/v1/analyses/{analysisId}/rollout")
    public RolloutPlanResponse rollout(@PathVariable UUID analysisId) {
        return RolloutPlanResponse.from(analysisId, rolloutPlanService.planFor(analysisId));
    }
}
