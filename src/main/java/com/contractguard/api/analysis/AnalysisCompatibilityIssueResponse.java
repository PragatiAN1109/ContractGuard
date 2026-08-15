package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisCompatibilityIssue;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnalysisCompatibilityIssueResponse(String issueType, String path, String reason) {

    static AnalysisCompatibilityIssueResponse from(AnalysisCompatibilityIssue issue) {
        return new AnalysisCompatibilityIssueResponse(
                issue.getIssueType(), issue.getPath(), issue.getReason());
    }
}
