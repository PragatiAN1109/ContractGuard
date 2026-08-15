package com.contractguard.api.compatibility;

import com.contractguard.api.schema.SchemaVersionSummary;
import com.contractguard.compatibility.CompatibilityReport;

import java.util.UUID;

/**
 * Structural compatibility only. Operational risk is a separate concept with its own endpoint,
 * and the two are never merged into a single verdict.
 */
public record CompatibilityReportResponse(UUID projectId,
                                          SchemaVersionSummary sourceVersion,
                                          SchemaVersionSummary targetVersion,
                                          CompatibilityResultsResponse results) {

    public static CompatibilityReportResponse from(CompatibilityReport report) {
        return new CompatibilityReportResponse(
                report.source().getProject().getId(),
                SchemaVersionSummary.from(report.source()),
                SchemaVersionSummary.from(report.target()),
                new CompatibilityResultsResponse(
                        CompatibilityModeResultResponse.from(report.backward()),
                        CompatibilityModeResultResponse.from(report.forward()),
                        CompatibilityModeResultResponse.from(report.full())));
    }
}
