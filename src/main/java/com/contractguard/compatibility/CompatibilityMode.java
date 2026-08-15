package com.contractguard.compatibility;

/**
 * Which direction of data flow is being checked.
 *
 * "Source" is the existing schema version and "target" the proposed one. Avro always answers the
 * question "can this reader read data written by that writer?", so each mode is a choice of which
 * schema plays the reader.
 */
public enum CompatibilityMode {

    /** Target reads data written with the source. Reader = target, writer = source. */
    BACKWARD,

    /** Source reads data written with the target. Reader = source, writer = target. */
    FORWARD,

    /** Both directions hold. Derived from the other two rather than checked separately. */
    FULL
}
