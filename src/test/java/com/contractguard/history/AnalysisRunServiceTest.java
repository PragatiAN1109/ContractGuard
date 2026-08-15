package com.contractguard.history;

import com.contractguard.compatibility.SchemaCompatibilityService;
import com.contractguard.consumeranalysis.OperationalRiskAnalysisService;
import com.contractguard.schema.SchemaVersion;
import com.contractguard.schema.SchemaVersionService;
import com.contractguard.shared.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisRunServiceTest {

    private final SchemaVersionService schemaVersionService = mock(SchemaVersionService.class);
    private final SchemaCompatibilityService compatibilityService = mock(SchemaCompatibilityService.class);
    private final OperationalRiskAnalysisService riskService = mock(OperationalRiskAnalysisService.class);
    private final AnalysisRunStore store = mock(AnalysisRunStore.class);

    private final AnalysisRunService service =
            new AnalysisRunService(schemaVersionService, compatibilityService, riskService, store);

    private final UUID projectId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private static int anyIntSafe() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    /** The service reads getVersionNumber() off both versions before creating the run. */
    private void stubSchemaVersions() {
        SchemaVersion source = mock(SchemaVersion.class);
        SchemaVersion target = mock(SchemaVersion.class);
        when(source.getVersionNumber()).thenReturn(1);
        when(target.getVersionNumber()).thenReturn(2);
        when(schemaVersionService.getById(projectId, sourceId)).thenReturn(source);
        when(schemaVersionService.getById(projectId, targetId)).thenReturn(target);
    }

    @Test
    @DisplayName("a bad schema reference fails before any run is created")
    void unknownSchemaVersionCreatesNoRun() {
        when(schemaVersionService.getById(projectId, sourceId))
                .thenThrow(new NotFoundException("Schema version not found"));

        assertThatThrownBy(() -> service.run(projectId, sourceId, targetId))
                .isInstanceOf(NotFoundException.class);

        verify(store, never()).createPending(any(), any(), any(), anyIntSafe(), anyIntSafe());
    }

    @Test
    @DisplayName("a failure during analysis is recorded and the run id is surfaced")
    void analysisFailureMarksTheRunFailed() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);
        stubSchemaVersions();
        when(store.createPending(any(), any(), any(), anyIntSafe(), anyIntSafe())).thenReturn(run);
        when(compatibilityService.analyse(projectId, sourceId, targetId))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.run(projectId, sourceId, targetId))
                .isInstanceOf(AnalysisFailedException.class);

        // The message is derived from the exception type, never its raw text.
        verify(store).markFailed(any(), eq("UNEXPECTED_ERROR"),
                eq("Analysis failed with IllegalStateException"));
        verify(store, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("a domain failure keeps its own message")
    void domainFailureKeepsItsMessage() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);
        stubSchemaVersions();
        when(store.createPending(any(), any(), any(), anyIntSafe(), anyIntSafe())).thenReturn(run);
        when(compatibilityService.analyse(projectId, sourceId, targetId))
                .thenThrow(new NotFoundException("Schema version 42 not found"));

        assertThatThrownBy(() -> service.run(projectId, sourceId, targetId))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessage("Schema version 42 not found");

        verify(store).markFailed(any(), eq("NotFoundException"), eq("Schema version 42 not found"));
    }

    @Test
    @DisplayName("the run is created and marked RUNNING before any analysis work starts")
    void statusMovesThroughRunningBeforeAnalysis() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);
        stubSchemaVersions();
        when(store.createPending(any(), any(), any(), anyIntSafe(), anyIntSafe())).thenReturn(run);
        when(compatibilityService.analyse(projectId, sourceId, targetId))
                .thenThrow(new IllegalStateException("stop here"));

        assertThatThrownBy(() -> service.run(projectId, sourceId, targetId));

        var order = inOrder(store, compatibilityService);
        order.verify(store).createPending(any(), any(), any(), anyIntSafe(), anyIntSafe());
        order.verify(store).markRunning(any());
        order.verify(compatibilityService).analyse(projectId, sourceId, targetId);
    }

    @Test
    @DisplayName("status transitions are enforced by the entity")
    void statusTransitionsAreEnforced() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(run.getStartedAt()).isNull();

        run.markRunning();
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(run.getStartedAt()).isNotNull();

        assertThatThrownBy(run::markRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot move an analysis from RUNNING");

        run.markCompleted("PASS", "FAIL", "FAIL", 2, "HIGH");
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getFindingCount()).isEqualTo(2);
        assertThat(run.getHighestSeverity()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("completing without first running is rejected")
    void cannotCompleteFromPending() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);

        assertThatThrownBy(() -> run.markCompleted("PASS", "PASS", "PASS", 0, "NONE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot move an analysis from PENDING");
    }

    @Test
    @DisplayName("a run can fail from any state and keeps its failure detail")
    void failureIsAlwaysAllowed() {
        AnalysisRun run = new AnalysisRun(projectId, sourceId, targetId, 1, 2);
        run.markRunning();
        run.markFailed("UNEXPECTED_ERROR", "Analysis failed with IllegalStateException");

        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getFailureCode()).isEqualTo("UNEXPECTED_ERROR");
        assertThat(run.getFailureMessage()).isEqualTo("Analysis failed with IllegalStateException");
        assertThat(run.getCompletedAt()).isNotNull();
    }
}
