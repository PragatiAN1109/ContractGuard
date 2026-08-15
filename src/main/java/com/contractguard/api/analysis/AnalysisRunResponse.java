package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRun;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * The full persisted snapshot. Compatibility and operational risk stay in separate sections and
 * are never combined into one verdict.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnalysisRunResponse(UUID analysisId,
                                  String status,
                                  UUID projectId,
                                  UUID sourceSchemaVersionId,
                                  UUID targetSchemaVersionId,
                                  int sourceVersion,
                                  int targetVersion,
                                  AnalysisCompatibilityResponse compatibility,
                                  AnalysisOperationalRiskResponse operationalRisk,
                                  String failureCode,
                                  String failureMessage,
                                  Instant createdAt,
                                  Instant startedAt,
                                  Instant completedAt) {

    public static AnalysisRunResponse from(AnalysisRun run) {
        return new AnalysisRunResponse(
                run.getId(),
                run.getStatus().name(),
                run.getProjectId(),
                run.getSourceSchemaVersionId(),
                run.getTargetSchemaVersionId(),
                run.getSourceVersionNumber(),
                run.getTargetVersionNumber(),
                AnalysisCompatibilityResponse.from(run),
                AnalysisOperationalRiskResponse.from(run),
                run.getFailureCode(),
                run.getFailureMessage(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getCompletedAt());
    }
}
