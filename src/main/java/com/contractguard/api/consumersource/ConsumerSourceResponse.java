package com.contractguard.api.consumersource;

import com.contractguard.consumeranalysis.ConsumerDefinition;

import java.util.List;

/** A registered consumer source, as it exists in the registry right now. */
public record ConsumerSourceResponse(String name,
                                     String description,
                                     String consumesSchema,
                                     String sourceType,
                                     String consumerSourceId,
                                     String revision,
                                     List<String> sourceFiles) {

    public static ConsumerSourceResponse from(ConsumerDefinition consumer) {
        String revision = consumer.revisionHash() == null
                ? null
                : consumer.revisionHash().substring(0, Math.min(12, consumer.revisionHash().length()));
        return new ConsumerSourceResponse(
                consumer.name(),
                consumer.description(),
                consumer.consumesSchema(),
                consumer.sourceType().name(),
                consumer.sourceId() == null ? null : consumer.sourceId().toString(),
                revision,
                consumer.sourceFiles().stream().map(f -> f.path()).sorted().toList());
    }
}
