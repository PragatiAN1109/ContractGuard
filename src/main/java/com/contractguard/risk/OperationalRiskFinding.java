package com.contractguard.risk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One operational risk, always backed by evidence.
 *
 * @param schemaPath the changed schema location, using the same dotted notation as the diff
 * @param attributes rule-specific detail in insertion order, e.g. newSymbol and fallbackSymbol
 */
public record OperationalRiskFinding(RiskRuleId ruleId,
                                     RiskSeverity severity,
                                     String consumer,
                                     String schemaPath,
                                     String reason,
                                     SourceEvidence evidence,
                                     Map<String, String> attributes) {

    public OperationalRiskFinding {
        // LinkedHashMap, not Map.copyOf: iteration order must stay stable for deterministic JSON.
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
