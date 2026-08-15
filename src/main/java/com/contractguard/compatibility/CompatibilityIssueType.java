package com.contractguard.compatibility;

/**
 * ContractGuard's own vocabulary for why a check failed.
 *
 * Deliberately mirrors Avro's categories without exposing Avro types through the API, so Avro
 * can add or rename a category without breaking clients.
 */
public enum CompatibilityIssueType {

    /** The reader requires a field the writer does not produce, and it has no default. */
    READER_FIELD_MISSING_DEFAULT_VALUE,

    /** The reader and writer types cannot be resolved, including promotions. */
    TYPE_MISMATCH,

    /** The writer can produce enum symbols the reader does not know, and the reader has no default. */
    MISSING_ENUM_SYMBOLS,

    /** The reader union has no branch matching a type the writer can produce. */
    MISSING_UNION_BRANCH,

    /** Named types disagree on name or namespace. */
    NAME_MISMATCH,

    /** Two fixed types declare different sizes. */
    FIXED_SIZE_MISMATCH,

    /** Avro reported a category this version of ContractGuard does not model. */
    UNKNOWN
}
