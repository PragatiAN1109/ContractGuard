package com.contractguard.history;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds populated AnalysisRun instances for tests.
 *
 * Lives in this package because the entity's lifecycle and child-adding methods are intentionally
 * package-private.
 */
public final class AnalysisRunFixture {

    private final AnalysisRun run;
    private int compatibilityPosition;
    private int findingPosition;
    private String backward = "PASS";
    private String forward = "PASS";
    private String full = "PASS";
    private int findingCount;
    private String highestSeverity = "NONE";

    private AnalysisRunFixture(int sourceVersion, int targetVersion) {
        this.run = new AnalysisRun(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                sourceVersion, targetVersion);
        this.run.markRunning();
    }

    public static AnalysisRunFixture analysis() {
        return new AnalysisRunFixture(1, 2);
    }

    public AnalysisRunFixture compatibility(String mode, String status, String... issuePaths) {
        AnalysisCompatibilityResult result =
                new AnalysisCompatibilityResult(mode, status, mode + " " + status, compatibilityPosition++);
        int issuePosition = 0;
        for (String path : issuePaths) {
            result.addIssue(new AnalysisCompatibilityIssue(
                    "TYPE_MISMATCH", path, "incompatible at " + path, issuePosition++));
        }
        run.addCompatibilityResult(result);
        switch (mode) {
            case "BACKWARD" -> backward = status;
            case "FORWARD" -> forward = status;
            case "FULL" -> full = status;
            default -> { }
        }
        return this;
    }

    /** Adds all three modes at once. Named distinctly so it cannot collide with the varargs form. */
    public AnalysisRunFixture allModes(String backwardStatus, String forwardStatus, String fullStatus) {
        return compatibility("BACKWARD", backwardStatus)
                .compatibility("FORWARD", forwardStatus)
                .compatibility("FULL", fullStatus);
    }

    public AnalysisRunFixture enumFallbackFinding(String consumer, String schemaPath,
                                                  String newSymbol, String fallbackSymbol) {
        return finding("ENUM_SEMANTIC_FALLBACK_RISK", consumer, schemaPath, newSymbol, fallbackSymbol);
    }

    public AnalysisRunFixture finding(String ruleId, String consumer, String schemaPath,
                                      String newSymbol, String fallbackSymbol) {
        AnalysisRiskFinding finding = new AnalysisRiskFinding(
                ruleId, "HIGH", consumer, schemaPath, "stored reason", findingPosition++);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("enumName", "OrderStatus");
        attributes.put("newSymbol", newSymbol);
        attributes.put("fallbackSymbol", fallbackSymbol);
        int position = 0;
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            finding.addAttribute(new AnalysisFindingAttribute(
                    attribute.getKey(), attribute.getValue(), position++));
        }
        finding.setEvidence(new AnalysisSourceEvidence(
                consumer + "/Handler.java", "Handler.java", 20, "case " + fallbackSymbol + " -> act();"));
        run.addFinding(finding);
        findingCount++;
        highestSeverity = "HIGH";
        return this;
    }

    public AnalysisRun completed() {
        run.markCompleted(backward, forward, full, findingCount, highestSeverity);
        return run;
    }

    public AnalysisRun failed() {
        run.markFailed("UNEXPECTED_ERROR", "Analysis failed with IllegalStateException");
        return run;
    }

    public AnalysisRun pending() {
        return new AnalysisRun(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 2);
    }
}
