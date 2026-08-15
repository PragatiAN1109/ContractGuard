package com.contractguard.schema;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SchemaComparisonService {

    private final SchemaVersionService schemaVersionService;
    private final AvroSchemaValidator validator;
    private final SchemaDiffEngine diffEngine;

    public SchemaComparisonService(SchemaVersionService schemaVersionService,
                                   AvroSchemaValidator validator,
                                   SchemaDiffEngine diffEngine) {
        this.schemaVersionService = schemaVersionService;
        this.validator = validator;
        this.diffEngine = diffEngine;
    }

    @Transactional(readOnly = true)
    public SchemaComparison compare(UUID projectId, UUID sourceVersionId, UUID targetVersionId) {
        SchemaVersion source = schemaVersionService.getById(projectId, sourceVersionId);
        SchemaVersion target = schemaVersionService.getById(projectId, targetVersionId);

        return new SchemaComparison(source, target, diffEngine.diff(
                validator.parse(source.getSchemaContent()),
                validator.parse(target.getSchemaContent())));
    }
}
