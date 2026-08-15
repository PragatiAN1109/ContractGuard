package com.contractguard.api.compatibility;

import com.contractguard.compatibility.CompatibilityModeResult;

import java.util.List;

public record CompatibilityModeResultResponse(String mode,
                                              String status,
                                              String summary,
                                              List<CompatibilityIssueResponse> issues) {

    public static CompatibilityModeResultResponse from(CompatibilityModeResult result) {
        return new CompatibilityModeResultResponse(
                result.mode().name(),
                result.status().name(),
                result.summary(),
                result.issues().stream().map(CompatibilityIssueResponse::from).toList());
    }
}
