package com.contractguard.api.risk;

import com.contractguard.api.consumersource.AnalysedConsumerResponse;
import com.contractguard.api.schema.SchemaVersionSummary;
import com.contractguard.consumeranalysis.OperationalRiskReport;

import java.util.List;
import java.util.UUID;

/**
 * Operational risk only. Structural compatibility has its own endpoint and is never folded in:
 * a change can be fully compatible and still carry HIGH risk.
 */
public record OperationalRiskReportResponse(UUID projectId,
                                            SchemaVersionSummary sourceVersion,
                                            SchemaVersionSummary targetVersion,
                                            String overallSeverity,
                                            int findingCount,
                                            List<OperationalRiskFindingResponse> findings,
                                            List<AnalysedConsumerResponse> analysedConsumers,
                                            List<String> warnings) {

    public static OperationalRiskReportResponse from(OperationalRiskReport report) {
        List<OperationalRiskFindingResponse> findings = report.findings().stream()
                .map(OperationalRiskFindingResponse::from)
                .toList();
        return new OperationalRiskReportResponse(
                report.source().getProject().getId(),
                SchemaVersionSummary.from(report.source()),
                SchemaVersionSummary.from(report.target()),
                report.overallSeverity().name(),
                findings.size(),
                findings,
                report.analysedConsumers().stream().map(AnalysedConsumerResponse::from).toList(),
                report.warnings());
    }
}
