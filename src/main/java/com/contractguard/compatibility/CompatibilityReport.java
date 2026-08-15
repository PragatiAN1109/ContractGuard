package com.contractguard.compatibility;

import com.contractguard.schema.SchemaVersion;

/**
 * Structural compatibility between two stored schema versions.
 *
 * This says nothing about whether deploying the change is safe. Operational risk is a separate
 * concept, produced by a separate module, and the two are never merged into one verdict.
 */
public record CompatibilityReport(SchemaVersion source,
                                  SchemaVersion target,
                                  CompatibilityModeResult backward,
                                  CompatibilityModeResult forward,
                                  CompatibilityModeResult full) {
}
