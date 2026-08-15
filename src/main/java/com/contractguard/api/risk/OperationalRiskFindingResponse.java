package com.contractguard.api.risk;

import com.contractguard.risk.OperationalRiskFinding;

import java.util.Map;

public record OperationalRiskFindingResponse(String ruleId,
                                             String severity,
                                             String consumer,
                                             String schemaPath,
                                             Map<String, String> attributes,
                                             SourceEvidenceResponse evidence,
                                             String reason) {

    public static OperationalRiskFindingResponse from(OperationalRiskFinding finding) {
        return new OperationalRiskFindingResponse(
                finding.ruleId().name(),
                finding.severity().name(),
                finding.consumer(),
                finding.schemaPath(),
                finding.attributes(),
                SourceEvidenceResponse.from(finding.evidence()),
                finding.reason());
    }
}
