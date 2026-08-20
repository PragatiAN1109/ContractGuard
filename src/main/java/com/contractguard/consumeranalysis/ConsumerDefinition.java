package com.contractguard.consumeranalysis;

import java.util.List;

/**
 * A downstream service whose source ContractGuard can analyse.
 *
 * @param consumesSchema full name of the Avro record it reads, e.g. com.example.orders.OrderEvent
 * @param sourceType    where the source came from; shown in the UI so findings are never mistaken
 *                      for the result of scanning an arbitrary repository
 */
public record ConsumerDefinition(String name,
                                 String description,
                                 String consumesSchema,
                                 ConsumerSourceType sourceType,
                                 List<ConsumerSourceFile> sourceFiles) {

    public ConsumerDefinition {
        sourceFiles = List.copyOf(sourceFiles);
    }
}
