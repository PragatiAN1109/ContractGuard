package com.contractguard.schema;

import com.contractguard.project.Project;
import com.contractguard.project.ProjectService;
import com.contractguard.shared.ConflictException;
import com.contractguard.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SchemaVersionService {

    private final SchemaVersionRepository schemaVersionRepository;
    private final ProjectService projectService;
    private final AvroSchemaValidator validator;

    public SchemaVersionService(SchemaVersionRepository schemaVersionRepository,
                                ProjectService projectService,
                                AvroSchemaValidator validator) {
        this.schemaVersionRepository = schemaVersionRepository;
        this.projectService = projectService;
        this.validator = validator;
    }

    @Transactional
    public SchemaVersion create(UUID projectId, String schemaContent) {
        Project project = projectService.getById(projectId);
        AvroSchemaValidator.NormalizedSchema normalized = validator.validate(schemaContent);

        schemaVersionRepository.findByProjectIdAndContentHash(projectId, normalized.contentHash())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "This schema is already stored in the project as version " + existing.getVersionNumber());
                });

        int versionNumber = schemaVersionRepository.findHighestVersionNumber(projectId).orElse(0) + 1;
        return schemaVersionRepository.save(
                new SchemaVersion(project, versionNumber, schemaContent, normalized.contentHash()));
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> findByProject(UUID projectId) {
        projectService.getById(projectId);
        return schemaVersionRepository.findByProjectIdOrderByVersionNumberAsc(projectId);
    }

    @Transactional(readOnly = true)
    public SchemaVersion getById(UUID projectId, UUID schemaVersionId) {
        projectService.getById(projectId);
        return schemaVersionRepository.findByIdAndProjectId(schemaVersionId, projectId)
                .orElseThrow(() -> new NotFoundException(
                        "Schema version " + schemaVersionId + " not found in project " + projectId));
    }
}
