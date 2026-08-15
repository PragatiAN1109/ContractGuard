package com.contractguard.rollout;

import java.util.List;

/**
 * Rollout guidance derived from a persisted analysis.
 *
 * @param limitations what this guidance does not cover; always populated
 */
public record RolloutPlan(RolloutStrategy strategy,
                          String summary,
                          List<RolloutStep> steps,
                          List<String> limitations) {

    public RolloutPlan {
        steps = List.copyOf(steps);
        limitations = List.copyOf(limitations);
    }
}
