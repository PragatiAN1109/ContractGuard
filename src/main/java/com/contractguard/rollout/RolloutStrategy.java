package com.contractguard.rollout;

/**
 * The shape of the recommended rollout. Advice, not certification: there is deliberately no
 * SAFE or UNSAFE value.
 */
public enum RolloutStrategy {

    /** A reader built from the target schema cannot read existing data; revise before rolling out. */
    BLOCKED_BY_COMPATIBILITY,

    /** Consumers must be updated and deployed before producers use the target schema. */
    CONSUMER_FIRST,

    /** No implemented rule fired. Not a statement that the change is safe. */
    NO_CONSTRAINT_IDENTIFIED
}
