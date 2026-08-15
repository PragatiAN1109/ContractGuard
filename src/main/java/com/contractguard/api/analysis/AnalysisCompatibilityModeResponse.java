package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisCompatibilityResult;

import java.util.List;

public record AnalysisCompatibilityModeResponse(String mode,
                                                String status,
                                                String summary,
                                                List<AnalysisCompatibilityIssueResponse> issues) {

    static AnalysisCompatibilityModeResponse from(AnalysisCompatibilityResult result) {
        return new AnalysisCompatibilityModeResponse(
                result.getMode(), result.getStatus(), result.getSummary(),
                result.getIssues().stream().map(AnalysisCompatibilityIssueResponse::from).toList());
    }
}
