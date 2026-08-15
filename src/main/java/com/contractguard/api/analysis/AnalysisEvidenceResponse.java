package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisSourceEvidence;

public record AnalysisEvidenceResponse(String sourceFile, String filePath, int line, String snippet) {

    static AnalysisEvidenceResponse from(AnalysisSourceEvidence evidence) {
        return new AnalysisEvidenceResponse(evidence.getFileName(), evidence.getFilePath(),
                evidence.getLine(), evidence.getSnippet());
    }
}
