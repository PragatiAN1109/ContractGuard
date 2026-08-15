package com.contractguard.consumeranalysis;

import java.util.List;

/**
 * Supplies consumers for a schema. Implemented by the built-in sample bundles today; a persisted
 * registry can be substituted later without touching the analysis code.
 */
public interface ConsumerRegistry {

    /** @param schemaFullName Avro record full name, e.g. com.example.orders.OrderEvent */
    List<ConsumerDefinition> findByConsumedSchema(String schemaFullName);
}
