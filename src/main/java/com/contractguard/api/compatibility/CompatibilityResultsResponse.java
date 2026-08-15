package com.contractguard.api.compatibility;

/** The three verdicts, keyed by mode so clients can address them by name. */
public record CompatibilityResultsResponse(CompatibilityModeResultResponse backward,
                                           CompatibilityModeResultResponse forward,
                                           CompatibilityModeResultResponse full) {
}
