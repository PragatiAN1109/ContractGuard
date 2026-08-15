package com.contractguard.history;

import java.util.UUID;

/** Analysis failed after the run was created. The FAILED run is persisted and fetchable. */
public class AnalysisFailedException extends RuntimeException {

    private final UUID analysisId;

    public AnalysisFailedException(UUID analysisId, String message) {
        super(message);
        this.analysisId = analysisId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }
}
