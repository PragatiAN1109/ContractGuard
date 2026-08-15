package com.contractguard.consumeranalysis;

import java.util.List;

/**
 * A downstream service whose source ContractGuard can analyse.
 *
 * @param consumesSchema full name of the Avro record it reads, e.g. com.example.orders.OrderEvent
 */
public record ConsumerDefinition(String name,
                                 String description,
                                 String consumesSchema,
                                 List<ConsumerSourceFile> sourceFiles) {

    public ConsumerDefinition {
        sourceFiles = List.copyOf(sourceFiles);
    }
}
