package com.contractguard.compatibility;

import java.util.List;

/**
 * The verdict for one mode.
 *
 * @param summary one sentence explaining the verdict in terms of readers and writers
 * @param issues  empty when the status is PASS. Always empty for FULL, which is derived from
 *                BACKWARD and FORWARD; read those for the detail.
 */
public record CompatibilityModeResult(CompatibilityMode mode,
                                      CompatibilityStatus status,
                                      String summary,
                                      List<CompatibilityIssue> issues) {

    public boolean isPass() {
        return status == CompatibilityStatus.PASS;
    }
}
