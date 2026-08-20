package com.contractguard.consumeranalysis;

import java.util.List;
import java.util.UUID;

/**
 * Supplies the consumers whose source should be analysed for a schema.
 *
 * Implemented by the built-in sample bundles and by the persisted registry of uploaded source.
 */
public interface ConsumerRegistry {

    /**
     * @param projectId      scopes registered sources; bundled samples ignore it
     * @param schemaFullName Avro record full name, e.g. com.example.orders.OrderEvent
     */
    List<ConsumerDefinition> findByConsumedSchema(UUID projectId, String schemaFullName);
}
