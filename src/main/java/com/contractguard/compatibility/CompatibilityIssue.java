package com.contractguard.compatibility;

/**
 * One reason a compatibility check failed.
 *
 * @param issueType what kind of incompatibility this is
 * @param path      dotted location in the reader schema for this mode, e.g.
 *                  {@code OrderEvent.items[].sku}; falls back to Avro's raw pointer when the
 *                  location cannot be resolved, and is null when Avro reports none
 * @param reason    a complete, deterministic sentence suitable for showing to an engineer
 */
public record CompatibilityIssue(CompatibilityIssueType issueType, String path, String reason) {
}
