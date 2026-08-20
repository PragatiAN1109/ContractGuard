package com.contractguard.consumeranalysis;

/** Where a consumer's source came from. External repository discovery is not implemented. */
public enum ConsumerSourceType {

    /** Bundled with a built-in sample on the classpath; not registered by a user. */
    BUILT_IN_SAMPLE,

    /** Java source the user uploaded and ContractGuard stored as an immutable revision. */
    UPLOADED_SOURCE
}
