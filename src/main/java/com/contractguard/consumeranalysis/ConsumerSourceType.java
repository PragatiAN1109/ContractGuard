package com.contractguard.consumeranalysis;

/**
 * Where a consumer's source came from.
 *
 * Only one value today: every registered consumer is bundled with a built-in sample. External
 * repository discovery is future work and deliberately has no constant yet.
 */
public enum ConsumerSourceType {
    BUILT_IN_SAMPLE
}
