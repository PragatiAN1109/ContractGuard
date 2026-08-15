package com.contractguard.compatibility;

import com.contractguard.schema.AvroSchemaValidator;
import com.contractguard.schema.SchemaVersion;
import com.contractguard.schema.SchemaVersionService;
import org.apache.avro.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SchemaCompatibilityService {

    private final SchemaVersionService schemaVersionService;
    private final AvroSchemaValidator validator;
    private final AvroCompatibilityEngine engine;

    public SchemaCompatibilityService(SchemaVersionService schemaVersionService,
                                      AvroSchemaValidator validator,
                                      AvroCompatibilityEngine engine) {
        this.schemaVersionService = schemaVersionService;
        this.validator = validator;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public CompatibilityReport analyse(UUID projectId, UUID sourceVersionId, UUID targetVersionId) {
        SchemaVersion source = schemaVersionService.getById(projectId, sourceVersionId);
        SchemaVersion target = schemaVersionService.getById(projectId, targetVersionId);

        Schema sourceSchema = validator.parse(source.getSchemaContent());
        Schema targetSchema = validator.parse(target.getSchemaContent());

        CompatibilityModeResult backward = engine.checkBackward(sourceSchema, targetSchema);
        CompatibilityModeResult forward = engine.checkForward(sourceSchema, targetSchema);

        return new CompatibilityReport(source, target, backward, forward,
                engine.deriveFull(backward, forward));
    }
}
