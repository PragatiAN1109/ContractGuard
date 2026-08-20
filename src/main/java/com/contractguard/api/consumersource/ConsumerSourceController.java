package com.contractguard.api.consumersource;

import com.contractguard.consumeranalysis.ConsumerRegistry;
import com.contractguard.schema.AvroSchemaValidator;
import com.contractguard.schema.SchemaVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ConsumerSourceController {

    private final SchemaVersionService schemaVersionService;
    private final AvroSchemaValidator validator;
    private final ConsumerRegistry consumerRegistry;

    public ConsumerSourceController(SchemaVersionService schemaVersionService,
                                    AvroSchemaValidator validator,
                                    ConsumerRegistry consumerRegistry) {
        this.schemaVersionService = schemaVersionService;
        this.validator = validator;
        this.consumerRegistry = consumerRegistry;
    }

    /** Which registered consumers an analysis of this schema would examine. */
    @GetMapping("/api/v1/projects/{projectId}/schemas/{schemaVersionId}/consumer-sources")
    public ConsumerSourcesResponse list(@PathVariable UUID projectId,
                                        @PathVariable UUID schemaVersionId) {
        String fullName = validator
                .parse(schemaVersionService.getById(projectId, schemaVersionId).getSchemaContent())
                .getFullName();

        var consumers = consumerRegistry.findByConsumedSchema(projectId, fullName).stream()
                .map(ConsumerSourceResponse::from)
                .toList();
        return new ConsumerSourcesResponse(fullName, consumers.size(), consumers);
    }
}
