package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRun;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** History listing. Carries no findings or evidence; fetch the analysis for those. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnalysisRunSummaryResponse(UUID analysisId,
                                         String status,
                                         int sourceVersion,
                                         int targetVersion,
                                         CompatibilitySummary compatibility,
                                         String highestSeverity,
                                         int findingCount,
                                         Instant createdAt,
                                         Instant completedAt) {

    public record CompatibilitySummary(String backward, String forward, String full) {}

    public static AnalysisRunSummaryResponse from(AnalysisRun run) {
        return new AnalysisRunSummaryResponse(
                run.getId(),
                run.getStatus().name(),
                run.getSourceVersionNumber(),
                run.getTargetVersionNumber(),
                new CompatibilitySummary(run.getBackwardStatus(), run.getForwardStatus(), run.getFullStatus()),
                run.getHighestSeverity(),
                run.getFindingCount(),
                run.getCreatedAt(),
                run.getCompletedAt());
    }
}
