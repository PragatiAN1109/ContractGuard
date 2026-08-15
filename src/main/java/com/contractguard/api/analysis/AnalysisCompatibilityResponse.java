package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisCompatibilityResult;
import com.contractguard.history.AnalysisRun;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AnalysisCompatibilityResponse(AnalysisCompatibilityModeResponse backward,
                                            AnalysisCompatibilityModeResponse forward,
                                            AnalysisCompatibilityModeResponse full) {

    static AnalysisCompatibilityResponse from(AnalysisRun run) {
        Map<String, AnalysisCompatibilityResult> byMode = run.getCompatibilityResults().stream()
                .collect(Collectors.toMap(AnalysisCompatibilityResult::getMode, Function.identity()));
        return new AnalysisCompatibilityResponse(
                mode(byMode, "BACKWARD"), mode(byMode, "FORWARD"), mode(byMode, "FULL"));
    }

    private static AnalysisCompatibilityModeResponse mode(Map<String, AnalysisCompatibilityResult> byMode,
                                                          String name) {
        AnalysisCompatibilityResult result = byMode.get(name);
        return result == null ? null : AnalysisCompatibilityModeResponse.from(result);
    }
}
