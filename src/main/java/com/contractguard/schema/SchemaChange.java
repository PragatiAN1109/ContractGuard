package com.contractguard.schema;

/**
 * One structural difference between two schemas.
 *
 * @param path       dotted location from the root record, e.g. {@code OrderEvent.items[].sku}
 * @param changeType what changed
 * @param oldValue   value in the source schema, or null when it did not exist there
 * @param newValue   value in the target schema, or null when it does not exist there
 */
public record SchemaChange(String path, SchemaChangeType changeType, String oldValue, String newValue) {
}
