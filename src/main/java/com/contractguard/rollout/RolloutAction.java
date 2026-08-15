package com.contractguard.rollout;

/** What a rollout step asks an engineer to do. */
public enum RolloutAction {
    REVISE_SCHEMA,
    RE_RUN_ANALYSIS,
    UPGRADE_CONSUMERS,
    UPDATE_CONSUMER,
    VERIFY_CONSUMER_DEPLOYMENT,
    DEPLOY_SCHEMA,
    BEGIN_PRODUCING
}
