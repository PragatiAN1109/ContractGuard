package com.contractguard.consumeranalysis;

import java.util.List;
import java.util.UUID;

/**
 * A consumer whose Java source can be analysed.
 *
 * @param consumesSchema full name of the Avro record it reads, e.g. com.example.orders.OrderEvent
 * @param sourceType     where the source came from, so findings are never mistaken for the result
 *                       of scanning an arbitrary repository
 * @param sourceId       the registered revision this came from, or null for a bundled sample
 * @param revisionHash   identifies the exact source content that produced a finding
 */
public record ConsumerDefinition(String name,
                                 String description,
                                 String consumesSchema,
                                 ConsumerSourceType sourceType,
                                 UUID sourceId,
                                 String revisionHash,
                                 List<ConsumerSourceFile> sourceFiles) {

    public ConsumerDefinition {
        sourceFiles = List.copyOf(sourceFiles);
    }
}
