package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRiskFinding;

import java.util.Map;

public record AnalysisFindingResponse(String ruleId,
                                      String severity,
                                      String consumer,
                                      String schemaPath,
                                      Map<String, String> attributes,
                                      AnalysisEvidenceResponse evidence,
                                      String reason) {

    static AnalysisFindingResponse from(AnalysisRiskFinding finding) {
        return new AnalysisFindingResponse(
                finding.getRuleId(), finding.getSeverity(), finding.getConsumer(),
                finding.getSchemaPath(), finding.getAttributes(),
                finding.getEvidence() == null ? null : AnalysisEvidenceResponse.from(finding.getEvidence()),
                finding.getReason());
    }
}
