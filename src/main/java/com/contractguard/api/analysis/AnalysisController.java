package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRun;
import com.contractguard.history.AnalysisRunService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
public class AnalysisController {

    private final AnalysisRunService analysisRunService;

    public AnalysisController(AnalysisRunService analysisRunService) {
        this.analysisRunService = analysisRunService;
    }

    /** Synchronous today; the status model already allows a later asynchronous executor. */
    @PostMapping("/api/v1/projects/{projectId}/analyses")
    public ResponseEntity<AnalysisRunResponse> create(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreateAnalysisRequest request,
                                                      UriComponentsBuilder uriBuilder) {
        AnalysisRun run = analysisRunService.run(
                projectId, request.sourceSchemaVersionId(), request.targetSchemaVersionId());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/analyses/{id}").build(run.getId()))
                .body(AnalysisRunResponse.from(run));
    }

    @GetMapping("/api/v1/projects/{projectId}/analyses")
    public List<AnalysisRunSummaryResponse> list(@PathVariable UUID projectId) {
        return analysisRunService.findByProject(projectId).stream()
                .map(AnalysisRunSummaryResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/analyses/{analysisId}")
    public AnalysisRunResponse get(@PathVariable UUID analysisId) {
        return AnalysisRunResponse.from(analysisRunService.getById(analysisId));
    }
}
