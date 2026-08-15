package com.contractguard.api.risk;

import com.contractguard.risk.SourceEvidence;

public record SourceEvidenceResponse(String sourceFile, String filePath, int line, String snippet) {

    public static SourceEvidenceResponse from(SourceEvidence evidence) {
        return new SourceEvidenceResponse(
                evidence.fileName(), evidence.filePath(), evidence.line(), evidence.snippet());
    }
}
