package com.contractguard.consumeranalysis;

import com.contractguard.risk.OperationalRiskFinding;
import com.contractguard.risk.RiskSeverity;
import com.contractguard.schema.SchemaVersion;

import java.util.List;

/**
 * Operational risk for one proposed change. Carries no compatibility verdict: the two are
 * independent results and are never merged.
 *
 * @param analysedConsumers consumers that were examined, whether or not they produced findings
 * @param warnings          source files that could not be analysed
 */
public record OperationalRiskReport(SchemaVersion source,
                                    SchemaVersion target,
                                    RiskSeverity overallSeverity,
                                    List<OperationalRiskFinding> findings,
                                    List<AnalysedConsumer> analysedConsumers,
                                    List<String> warnings) {

    public OperationalRiskReport {
        findings = List.copyOf(findings);
        analysedConsumers = List.copyOf(analysedConsumers);
        warnings = List.copyOf(warnings);
    }
}
