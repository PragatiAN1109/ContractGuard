package com.contractguard.consumeranalysis;

import com.contractguard.risk.OperationalRiskFinding;
import com.contractguard.risk.RiskRuleId;
import com.contractguard.risk.RiskSeverity;
import com.contractguard.risk.SourceEvidence;
import com.contractguard.schema.SchemaChange;
import com.contractguard.schema.SchemaChangeType;
import com.contractguard.schema.SchemaEnumIndex;
import org.apache.avro.Schema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Flags a new enum symbol that an older consumer will silently see as the enum's default, where
 * that default already drives business logic.
 *
 * All of these must hold before anything is reported:
 * <ol>
 *   <li>the diff adds a symbol at some enum path,</li>
 *   <li>the enum existed in the source schema and declares a default symbol,</li>
 *   <li>a consumer names that default symbol in a switch label or an equality comparison,</li>
 *   <li>that consumer never mentions the new symbol, so it cannot already be aware of it.</li>
 * </ol>
 */
@Component
public class EnumSemanticFallbackRule {

    private static final Comparator<OperationalRiskFinding> STABLE_ORDER =
            Comparator.comparing(OperationalRiskFinding::consumer)
                    .thenComparing(OperationalRiskFinding::schemaPath)
                    .thenComparing(finding -> finding.evidence().filePath())
                    .thenComparingInt(finding -> finding.evidence().line())
                    .thenComparing(finding -> finding.attributes().getOrDefault("newSymbol", ""));

    private final JavaEnumUsageScanner scanner;

    public EnumSemanticFallbackRule(JavaEnumUsageScanner scanner) {
        this.scanner = scanner;
    }

    /** @param warnings collects source files that could not be analysed */
    public List<OperationalRiskFinding> apply(List<SchemaChange> changes,
                                              Schema sourceSchema,
                                              List<ConsumerDefinition> consumers,
                                              List<String> warnings) {
        Map<String, Set<String>> addedSymbolsByPath = addedSymbolsByPath(changes);
        if (addedSymbolsByPath.isEmpty() || consumers.isEmpty()) {
            return List.of();
        }

        Map<String, Schema> sourceEnums = SchemaEnumIndex.enumsByPath(sourceSchema);
        List<OperationalRiskFinding> findings = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : addedSymbolsByPath.entrySet()) {
            String schemaPath = entry.getKey();
            Schema sourceEnum = sourceEnums.get(schemaPath);

            // The enum is new, so no older consumer was generated against it.
            if (sourceEnum == null) {
                continue;
            }
            // Without a default the old reader fails outright; that is a compatibility problem,
            // reported by the compatibility engine, not a silent semantic one.
            String fallbackSymbol = sourceEnum.getEnumDefault();
            if (fallbackSymbol == null) {
                continue;
            }

            for (ConsumerDefinition consumer : consumers) {
                findings.addAll(analyseConsumer(consumer, sourceEnum, schemaPath,
                        entry.getValue(), fallbackSymbol, warnings));
            }
        }

        findings.sort(STABLE_ORDER);
        return List.copyOf(findings);
    }

    private List<OperationalRiskFinding> analyseConsumer(ConsumerDefinition consumer,
                                                         Schema sourceEnum,
                                                         String schemaPath,
                                                         Set<String> addedSymbols,
                                                         String fallbackSymbol,
                                                         List<String> warnings) {
        Set<String> recognised = new LinkedHashSet<>(sourceEnum.getEnumSymbols());
        recognised.addAll(addedSymbols);

        Map<ConsumerSourceFile, List<EnumUsage>> usagesByFile = new LinkedHashMap<>();
        for (ConsumerSourceFile file : consumer.sourceFiles()) {
            JavaEnumUsageScanner.ScanResult result =
                    scanner.scan(file, sourceEnum.getName(), recognised);
            result.warning().ifPresent(warning -> warnings.add(consumer.name() + " / " + warning));
            usagesByFile.put(file, result.usages());
        }

        // A consumer that already names the new symbol was generated against the new schema, so
        // nothing falls back for it.
        boolean awareOfNewSymbol = usagesByFile.values().stream()
                .flatMap(List::stream)
                .anyMatch(usage -> addedSymbols.contains(usage.symbol()));
        if (awareOfNewSymbol) {
            return List.of();
        }

        List<OperationalRiskFinding> findings = new ArrayList<>();
        for (Map.Entry<ConsumerSourceFile, List<EnumUsage>> entry : usagesByFile.entrySet()) {
            for (EnumUsage usage : entry.getValue()) {
                if (!usage.symbol().equals(fallbackSymbol)) {
                    continue;
                }
                for (String newSymbol : new TreeSet<>(addedSymbols)) {
                    findings.add(finding(consumer, entry.getKey(), usage, schemaPath,
                            newSymbol, fallbackSymbol, sourceEnum.getName()));
                }
            }
        }
        return findings;
    }

    private static OperationalRiskFinding finding(ConsumerDefinition consumer,
                                                  ConsumerSourceFile file,
                                                  EnumUsage usage,
                                                  String schemaPath,
                                                  String newSymbol,
                                                  String fallbackSymbol,
                                                  String enumName) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("enumName", enumName);
        attributes.put("newSymbol", newSymbol);
        attributes.put("fallbackSymbol", fallbackSymbol);
        attributes.put("usageKind", usage.kind().name());

        String reason = "The proposed schema adds '" + newSymbol + "' to " + schemaPath
                + ". Because the previous enum declares '" + fallbackSymbol + "' as its default, a "
                + "consumer still generated against the previous schema resolves '" + newSymbol
                + "' to '" + fallbackSymbol + "' instead of failing. This consumer gives '"
                + fallbackSymbol + "' its own business behaviour, so records carrying '" + newSymbol
                + "' would be handled as if they were '" + fallbackSymbol + "'.";

        return new OperationalRiskFinding(
                RiskRuleId.ENUM_SEMANTIC_FALLBACK_RISK,
                RiskSeverity.HIGH,
                consumer.name(),
                schemaPath,
                reason,
                new SourceEvidence(file.path(), file.fileName(), usage.line(), usage.snippet()),
                attributes);
    }

    private static Map<String, Set<String>> addedSymbolsByPath(List<SchemaChange> changes) {
        Map<String, Set<String>> byPath = new LinkedHashMap<>();
        for (SchemaChange change : changes) {
            if (change.changeType() == SchemaChangeType.ENUM_SYMBOL_ADDED && change.newValue() != null) {
                byPath.computeIfAbsent(change.path(), key -> new LinkedHashSet<>()).add(change.newValue());
            }
        }
        return byPath;
    }
}
