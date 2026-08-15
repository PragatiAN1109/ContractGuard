package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRun;

import java.util.List;

public record AnalysisOperationalRiskResponse(String overallSeverity,
                                              int findingCount,
                                              List<AnalysisFindingResponse> findings) {

    static AnalysisOperationalRiskResponse from(AnalysisRun run) {
        List<AnalysisFindingResponse> findings = run.getFindings().stream()
                .map(AnalysisFindingResponse::from)
                .toList();
        return new AnalysisOperationalRiskResponse(
                run.getHighestSeverity(), findings.size(), findings);
    }
}
