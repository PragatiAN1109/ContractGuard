package com.contractguard.api.consumersource;

import com.contractguard.consumeranalysis.ConsumerDefinition;

import java.util.List;

/** A registered consumer source, as it exists in the registry right now. */
public record ConsumerSourceResponse(String name,
                                     String description,
                                     String consumesSchema,
                                     String sourceType,
                                     List<String> sourceFiles) {

    public static ConsumerSourceResponse from(ConsumerDefinition consumer) {
        return new ConsumerSourceResponse(
                consumer.name(),
                consumer.description(),
                consumer.consumesSchema(),
                consumer.sourceType().name(),
                consumer.sourceFiles().stream().map(f -> f.path()).sorted().toList());
    }
}
