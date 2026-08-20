package com.contractguard.consumeranalysis;

import com.contractguard.risk.OperationalRiskFinding;
import com.contractguard.risk.RiskSeverity;
import com.contractguard.schema.SchemaComparison;
import com.contractguard.schema.SchemaComparisonService;
import com.contractguard.schema.AvroSchemaValidator;
import org.apache.avro.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Runs the consumer-aware risk rules for a pair of stored schema versions.
 *
 * The diff comes from the existing engine rather than being recomputed here.
 */
@Service
public class OperationalRiskAnalysisService {

    private final SchemaComparisonService comparisonService;
    private final AvroSchemaValidator validator;
    private final ConsumerRegistry consumerRegistry;
    private final EnumSemanticFallbackRule enumSemanticFallbackRule;

    public OperationalRiskAnalysisService(SchemaComparisonService comparisonService,
                                          AvroSchemaValidator validator,
                                          ConsumerRegistry consumerRegistry,
                                          EnumSemanticFallbackRule enumSemanticFallbackRule) {
        this.comparisonService = comparisonService;
        this.validator = validator;
        this.consumerRegistry = consumerRegistry;
        this.enumSemanticFallbackRule = enumSemanticFallbackRule;
    }

    @Transactional(readOnly = true)
    public OperationalRiskReport analyse(UUID projectId, UUID sourceVersionId, UUID targetVersionId) {
        SchemaComparison comparison = comparisonService.compare(projectId, sourceVersionId, targetVersionId);
        Schema sourceSchema = validator.parse(comparison.source().getSchemaContent());

        List<ConsumerDefinition> consumers =
                consumerRegistry.findByConsumedSchema(sourceSchema.getFullName());

        List<String> warnings = new ArrayList<>();
        List<OperationalRiskFinding> findings = enumSemanticFallbackRule.apply(
                comparison.changes(), sourceSchema, consumers, warnings);

        return new OperationalRiskReport(
                comparison.source(),
                comparison.target(),
                findings.isEmpty() ? RiskSeverity.NONE : highest(findings),
                findings,
                consumers.stream().map(AnalysedConsumer::from)
                        .sorted(Comparator.comparing(AnalysedConsumer::name)).toList(),
                warnings.stream().sorted().toList());
    }

    private static RiskSeverity highest(List<OperationalRiskFinding> findings) {
        return findings.stream()
                .map(OperationalRiskFinding::severity)
                .max(Comparator.naturalOrder())
                .orElse(RiskSeverity.NONE);
    }
}
