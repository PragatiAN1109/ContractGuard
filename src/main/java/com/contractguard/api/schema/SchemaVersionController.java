package com.contractguard.api.schema;

import com.contractguard.schema.SchemaComparisonService;
import com.contractguard.schema.SchemaVersion;
import com.contractguard.schema.SchemaVersionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/schemas")
public class SchemaVersionController {

    private final SchemaVersionService schemaVersionService;
    private final SchemaComparisonService schemaComparisonService;

    public SchemaVersionController(SchemaVersionService schemaVersionService,
                                   SchemaComparisonService schemaComparisonService) {
        this.schemaVersionService = schemaVersionService;
        this.schemaComparisonService = schemaComparisonService;
    }

    @PostMapping
    public ResponseEntity<SchemaVersionResponse> create(@PathVariable UUID projectId,
                                                        @Valid @RequestBody CreateSchemaVersionRequest request,
                                                        UriComponentsBuilder uriBuilder) {
        SchemaVersion created = schemaVersionService.create(projectId, request.schemaContent());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/projects/{projectId}/schemas/{id}")
                        .build(projectId, created.getId()))
                .body(SchemaVersionResponse.from(created));
    }

    @GetMapping
    public List<SchemaVersionSummary> list(@PathVariable UUID projectId) {
        return schemaVersionService.findByProject(projectId).stream().map(SchemaVersionSummary::from).toList();
    }

    @GetMapping("/{schemaVersionId}")
    public SchemaVersionResponse get(@PathVariable UUID projectId, @PathVariable UUID schemaVersionId) {
        return SchemaVersionResponse.from(schemaVersionService.getById(projectId, schemaVersionId));
    }

    /** Structural diff from one stored version to another in the same project. */
    @GetMapping("/{sourceSchemaVersionId}/diff/{targetSchemaVersionId}")
    public SchemaComparisonResponse diff(@PathVariable UUID projectId,
                                         @PathVariable UUID sourceSchemaVersionId,
                                         @PathVariable UUID targetSchemaVersionId) {
        return SchemaComparisonResponse.from(
                schemaComparisonService.compare(projectId, sourceSchemaVersionId, targetSchemaVersionId));
    }
}
