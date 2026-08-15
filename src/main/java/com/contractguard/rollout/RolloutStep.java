package com.contractguard.rollout;

/**
 * @param order  1-based position in the sequence
 * @param target what the step acts on, e.g. a consumer name or a schema version
 * @param reason why this step exists, derived from the persisted analysis
 */
public record RolloutStep(int order, RolloutAction action, String target, String reason) {
}
