package com.contractguard.history;

import com.contractguard.compatibility.CompatibilityReport;
import com.contractguard.compatibility.SchemaCompatibilityService;
import com.contractguard.consumeranalysis.OperationalRiskAnalysisService;
import com.contractguard.consumeranalysis.OperationalRiskReport;
import com.contractguard.schema.InvalidAvroSchemaException;
import com.contractguard.schema.SchemaVersion;
import com.contractguard.schema.SchemaVersionService;
import com.contractguard.shared.ConflictException;
import com.contractguard.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Runs a complete analysis and stores the result.
 *
 * Deliberately not @Transactional: a single transaction spanning the whole run would roll the
 * AnalysisRun row back on failure, losing the very record that says the analysis failed. All
 * writes go through {@link AnalysisRunStore} in short, separately committed transactions.
 */
@Service
public class AnalysisRunService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunService.class);

    private final SchemaVersionService schemaVersionService;
    private final SchemaCompatibilityService compatibilityService;
    private final OperationalRiskAnalysisService riskAnalysisService;
    private final AnalysisRunStore store;

    public AnalysisRunService(SchemaVersionService schemaVersionService,
                              SchemaCompatibilityService compatibilityService,
                              OperationalRiskAnalysisService riskAnalysisService,
                              AnalysisRunStore store) {
        this.schemaVersionService = schemaVersionService;
        this.compatibilityService = compatibilityService;
        this.riskAnalysisService = riskAnalysisService;
        this.store = store;
    }

    public AnalysisRun run(UUID projectId, UUID sourceVersionId, UUID targetVersionId) {
        // Resolved first so a bad reference is a 404 with no run created. Also enforces that both
        // versions belong to this project.
        SchemaVersion source = schemaVersionService.getById(projectId, sourceVersionId);
        SchemaVersion target = schemaVersionService.getById(projectId, targetVersionId);

        AnalysisRun run = store.createPending(projectId, sourceVersionId, targetVersionId,
                source.getVersionNumber(), target.getVersionNumber());
        UUID analysisId = run.getId();
        store.markRunning(analysisId);

        try {
            CompatibilityReport compatibility =
                    compatibilityService.analyse(projectId, sourceVersionId, targetVersionId);
            OperationalRiskReport risk =
                    riskAnalysisService.analyse(projectId, sourceVersionId, targetVersionId);
            store.complete(analysisId, compatibility, risk);
        } catch (RuntimeException e) {
            Failure failure = Failure.from(e);
            log.warn("Analysis {} failed: {} - {}", analysisId, failure.code(), failure.message(), e);
            store.markFailed(analysisId, failure.code(), failure.message());
            throw new AnalysisFailedException(analysisId, failure.message());
        }

        return store.loadSnapshot(analysisId);
    }

    public AnalysisRun getById(UUID analysisId) {
        return store.loadSnapshot(analysisId);
    }

    public List<AnalysisRun> findByProject(UUID projectId) {
        return store.findByProject(projectId);
    }

    /** Only known domain messages are stored; anything else is reduced to its type. */
    private record Failure(String code, String message) {

        static Failure from(RuntimeException e) {
            if (e instanceof NotFoundException || e instanceof ConflictException
                    || e instanceof InvalidAvroSchemaException) {
                return new Failure(e.getClass().getSimpleName(), e.getMessage());
            }
            return new Failure("UNEXPECTED_ERROR",
                    "Analysis failed with " + e.getClass().getSimpleName());
        }
    }
}
