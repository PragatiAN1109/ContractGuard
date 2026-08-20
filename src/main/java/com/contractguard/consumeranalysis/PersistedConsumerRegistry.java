package com.contractguard.consumeranalysis;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Consumers the user registered by uploading Java source. Active revisions only. */
@Component
public class PersistedConsumerRegistry implements ConsumerRegistry {

    private final ConsumerSourceRepository repository;

    public PersistedConsumerRegistry(ConsumerSourceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumerDefinition> findByConsumedSchema(UUID projectId, String schemaFullName) {
        return repository
                .findByProjectIdAndConsumesSchemaAndSupersededAtIsNullOrderByServiceNameAsc(
                        projectId, schemaFullName)
                .stream()
                .map(ConsumerSource::toDefinition)
                .toList();
    }
}
