package com.contractguard.history;

import com.contractguard.compatibility.CompatibilityModeResult;
import com.contractguard.compatibility.CompatibilityReport;
import com.contractguard.consumeranalysis.OperationalRiskReport;
import com.contractguard.risk.OperationalRiskFinding;
import com.contractguard.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every write transaction for an analysis run.
 *
 * Separate from the orchestrator on purpose: the orchestrator must stay non-transactional so a
 * failure cannot roll back the run record itself, and Spring only applies @Transactional across
 * bean boundaries.
 */
@Service
public class AnalysisRunStore {

    private final AnalysisRunRepository repository;

    public AnalysisRunStore(AnalysisRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AnalysisRun createPending(UUID projectId, UUID sourceVersionId, UUID targetVersionId,
                                     int sourceVersionNumber, int targetVersionNumber) {
        return repository.save(new AnalysisRun(projectId, sourceVersionId, targetVersionId,
                sourceVersionNumber, targetVersionNumber));
    }

    @Transactional
    public void markRunning(UUID analysisId) {
        load(analysisId).markRunning();
    }

    /** Results and the COMPLETED status commit together, so a run is never partially populated. */
    @Transactional
    public void complete(UUID analysisId, CompatibilityReport compatibility, OperationalRiskReport risk) {
        AnalysisRun run = load(analysisId);

        int position = 0;
        for (CompatibilityModeResult mode : List.of(compatibility.backward(), compatibility.forward(),
                compatibility.full())) {
            run.addCompatibilityResult(toResult(mode, position++));
        }

        int findingPosition = 0;
        for (OperationalRiskFinding finding : risk.findings()) {
            run.addFinding(toFinding(finding, findingPosition++));
        }

        int consumerPosition = 0;
        for (var consumer : risk.analysedConsumers()) {
            run.addAnalysedConsumer(new AnalysisAnalysedConsumer(
                    consumer.name(), consumer.sourceType().name(), consumer.sourceId(),
                    consumer.revisionHash(), consumer.sourceFiles(), consumerPosition++));
        }

        run.markCompleted(
                compatibility.backward().status().name(),
                compatibility.forward().status().name(),
                compatibility.full().status().name(),
                risk.findings().size(),
                risk.overallSeverity().name());
    }

    /** REQUIRES_NEW so the failure record survives even if a caller later wraps this in a transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID analysisId, String failureCode, String failureMessage) {
        load(analysisId).markFailed(failureCode, failureMessage);
    }

    /** Loads a run with its children initialized, so mapping can happen outside the transaction. */
    @Transactional(readOnly = true)
    public AnalysisRun loadSnapshot(UUID analysisId) {
        AnalysisRun run = repository.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Analysis " + analysisId + " not found"));
        run.getCompatibilityResults().forEach(result -> result.getIssues().size());
        run.getAnalysedConsumers().size();
        run.getFindings().forEach(finding -> {
            finding.getAttributes().size();
            if (finding.getEvidence() != null) {
                finding.getEvidence().getId();
            }
        });
        return run;
    }

    @Transactional(readOnly = true)
    public List<AnalysisRun> findByProject(UUID projectId) {
        return repository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
    }

    private AnalysisRun load(UUID analysisId) {
        return repository.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Analysis " + analysisId + " not found"));
    }

    private static AnalysisCompatibilityResult toResult(CompatibilityModeResult mode, int position) {
        AnalysisCompatibilityResult result = new AnalysisCompatibilityResult(
                mode.mode().name(), mode.status().name(), mode.summary(), position);
        int issuePosition = 0;
        for (var issue : mode.issues()) {
            result.addIssue(new AnalysisCompatibilityIssue(
                    issue.issueType().name(), issue.path(), issue.reason(), issuePosition++));
        }
        return result;
    }

    private static AnalysisRiskFinding toFinding(OperationalRiskFinding finding, int position) {
        AnalysisRiskFinding stored = new AnalysisRiskFinding(
                finding.ruleId().name(), finding.severity().name(), finding.consumer(),
                finding.schemaPath(), finding.reason(), position);

        int attributePosition = 0;
        for (Map.Entry<String, String> attribute : finding.attributes().entrySet()) {
            stored.addAttribute(new AnalysisFindingAttribute(
                    attribute.getKey(), attribute.getValue(), attributePosition++));
        }
        if (finding.evidence() != null) {
            stored.setEvidence(new AnalysisSourceEvidence(
                    finding.evidence().filePath(), finding.evidence().fileName(),
                    finding.evidence().line(), finding.evidence().snippet()));
        }
        return stored;
    }
}
