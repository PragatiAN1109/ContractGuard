package com.contractguard.api.compatibility;

import com.contractguard.compatibility.SchemaCompatibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/schemas")
public class CompatibilityController {

    private final SchemaCompatibilityService compatibilityService;

    public CompatibilityController(SchemaCompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
    }

    /** Structural compatibility from one stored version to another in the same project. */
    @GetMapping("/{sourceSchemaVersionId}/compatibility/{targetSchemaVersionId}")
    public CompatibilityReportResponse compatibility(@PathVariable UUID projectId,
                                                     @PathVariable UUID sourceSchemaVersionId,
                                                     @PathVariable UUID targetSchemaVersionId) {
        return CompatibilityReportResponse.from(
                compatibilityService.analyse(projectId, sourceSchemaVersionId, targetSchemaVersionId));
    }
}
