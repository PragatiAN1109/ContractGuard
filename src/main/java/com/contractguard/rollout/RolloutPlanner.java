package com.contractguard.rollout;

import com.contractguard.history.AnalysisCompatibilityIssue;
import com.contractguard.history.AnalysisCompatibilityResult;
import com.contractguard.history.AnalysisRiskFinding;
import com.contractguard.history.AnalysisRun;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a persisted analysis into an ordered rollout sequence.
 *
 * Reads only the stored snapshot: no diff, compatibility or source analysis is repeated, and no
 * consumer file is opened. The same run therefore always yields the same plan.
 */
@Component
public class RolloutPlanner {

    private static final String RULE_ENUM_FALLBACK = "ENUM_SEMANTIC_FALLBACK_RISK";

    private static final List<String> LIMITATIONS = List.of(
            "Guidance covers only the compatibility and operational-risk rules ContractGuard "
                    + "currently implements.",
            "No implemented rule firing is not proof that the change is safe to deploy.",
            "Only consumers known to ContractGuard at analysis time were examined.");

    public RolloutPlan plan(AnalysisRun run) {
        Map<String, AnalysisCompatibilityResult> byMode = new TreeMap<>();
        run.getCompatibilityResults().forEach(result -> byMode.put(result.getMode(), result));

        boolean backwardFails = fails(byMode.get("BACKWARD"));
        boolean forwardFails = fails(byMode.get("FORWARD"));

        List<AnalysisRiskFinding> enumFindings = run.getFindings().stream()
                .filter(finding -> RULE_ENUM_FALLBACK.equals(finding.getRuleId()))
                .toList();

        String schemaLabel = "schema version " + run.getTargetVersionNumber();
        List<RolloutStep> steps = new ArrayList<>();

        if (backwardFails) {
            steps.addAll(backwardSteps(byMode.get("BACKWARD"), schemaLabel));
            steps.addAll(consumerSteps(enumFindings));
            return assemble(RolloutStrategy.BLOCKED_BY_COMPATIBILITY,
                    "A reader built from " + schemaLabel + " cannot decode records already written "
                            + "with the source schema, so the schema should be revised before rollout.",
                    steps);
        }

        if (forwardFails) {
            steps.add(new RolloutStep(0, RolloutAction.UPGRADE_CONSUMERS,
                    "consumers reading schema version " + run.getSourceVersionNumber(),
                    "FORWARD compatibility fails: readers built from the source schema cannot decode "
                            + "records produced with " + schemaLabel + affectedPaths(byMode.get("FORWARD"))
                            + ". Upgrade or retire those consumers before producers use the target schema."));
        }
        steps.addAll(consumerSteps(enumFindings));

        if (steps.isEmpty()) {
            return assemble(RolloutStrategy.NO_CONSTRAINT_IDENTIFIED,
                    "No rollout constraint was identified by the currently implemented ContractGuard rules.",
                    steps);
        }

        steps.add(new RolloutStep(0, RolloutAction.DEPLOY_SCHEMA, schemaLabel,
                "Introduce the target schema only after the steps above are complete."));
        if (!enumFindings.isEmpty()) {
            steps.add(new RolloutStep(0, RolloutAction.BEGIN_PRODUCING, schemaLabel,
                    "Start emitting " + newSymbolSummary(enumFindings)
                            + " only once every affected consumer handles it explicitly."));
        }

        return assemble(RolloutStrategy.CONSUMER_FIRST, consumerFirstSummary(enumFindings, forwardFails), steps);
    }

    private static List<RolloutStep> backwardSteps(AnalysisCompatibilityResult backward, String schemaLabel) {
        return List.of(
                new RolloutStep(0, RolloutAction.REVISE_SCHEMA, schemaLabel,
                        "BACKWARD compatibility fails: a reader built from the target schema cannot decode "
                                + "records written with the source schema" + affectedPaths(backward)
                                + ". During a mixed-version rollout the updated consumers would fail on "
                                + "data already in flight."),
                new RolloutStep(0, RolloutAction.RE_RUN_ANALYSIS, schemaLabel,
                        "Re-run the analysis after revising the schema to confirm the blocker is resolved."));
    }

    /** One update and one verify step per affected consumer, whatever the number of findings. */
    private static List<RolloutStep> consumerSteps(List<AnalysisRiskFinding> findings) {
        Map<String, Set<String>> reasonsByConsumer = new TreeMap<>();
        for (AnalysisRiskFinding finding : findings) {
            reasonsByConsumer
                    .computeIfAbsent(finding.getConsumer(), key -> new TreeSet<>())
                    .add(fallbackSentence(finding));
        }

        List<RolloutStep> steps = new ArrayList<>();
        reasonsByConsumer.forEach((consumer, reasons) -> {
            steps.add(new RolloutStep(0, RolloutAction.UPDATE_CONSUMER, consumer,
                    String.join(" ", reasons)));
            steps.add(new RolloutStep(0, RolloutAction.VERIFY_CONSUMER_DEPLOYMENT, consumer,
                    "Confirm the updated " + consumer + " is deployed everywhere before producers "
                            + "emit the new value."));
        });
        return steps;
    }

    private static String fallbackSentence(AnalysisRiskFinding finding) {
        Map<String, String> attributes = finding.getAttributes();
        String newSymbol = attributes.getOrDefault("newSymbol", "the new symbol");
        String fallbackSymbol = attributes.getOrDefault("fallbackSymbol", "the enum default");
        return finding.getConsumer() + " may interpret '" + newSymbol + "' as '" + fallbackSymbol
                + "' at " + finding.getSchemaPath() + "; handle '" + newSymbol + "' explicitly.";
    }

    private static String newSymbolSummary(List<AnalysisRiskFinding> findings) {
        Set<String> symbols = new TreeSet<>();
        findings.forEach(finding ->
                symbols.add("'" + finding.getAttributes().getOrDefault("newSymbol", "the new symbol")
                        + "' at " + finding.getSchemaPath()));
        return String.join(", ", symbols);
    }

    private static String consumerFirstSummary(List<AnalysisRiskFinding> findings, boolean forwardFails) {
        Set<String> consumers = new TreeSet<>();
        findings.forEach(finding -> consumers.add(finding.getConsumer()));

        if (consumers.isEmpty()) {
            return "Consumers of the source schema should be upgraded or retired before producers "
                    + "use the target schema.";
        }
        String subject = consumers.size() == 1
                ? "One affected consumer (" + String.join(", ", consumers) + ")"
                : consumers.size() + " affected consumers (" + String.join(", ", consumers) + ")";
        return subject + " should be updated and deployed before producers use the target schema"
                + (forwardFails ? ", and FORWARD compatibility also fails for older readers." : ".");
    }

    /** Drops steps that repeat an action against the same target, then numbers what remains. */
    private static RolloutPlan assemble(RolloutStrategy strategy, String summary, List<RolloutStep> steps) {
        Set<String> seen = new LinkedHashSet<>();
        List<RolloutStep> ordered = new ArrayList<>();
        for (RolloutStep step : steps) {
            if (seen.add(step.action().name() + "|" + step.target())) {
                ordered.add(new RolloutStep(ordered.size() + 1, step.action(), step.target(), step.reason()));
            }
        }
        return new RolloutPlan(strategy, summary, ordered, LIMITATIONS);
    }

    private static boolean fails(AnalysisCompatibilityResult result) {
        return result != null && "FAIL".equals(result.getStatus());
    }

    private static String affectedPaths(AnalysisCompatibilityResult result) {
        if (result == null) {
            return "";
        }
        Set<String> paths = new TreeSet<>();
        for (AnalysisCompatibilityIssue issue : result.getIssues()) {
            if (issue.getPath() != null) {
                paths.add(issue.getPath());
            }
        }
        return paths.isEmpty() ? "" : " (" + String.join(", ", paths) + ")";
    }
}
