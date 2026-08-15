package com.contractguard.api.schema;

import com.contractguard.schema.SchemaChange;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Nulls are serialized explicitly, overriding the global non-null default: in a diff, a null
 * oldValue is the meaningful statement "this did not exist in the source schema".
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SchemaChangeResponse(String path, String changeType, String oldValue, String newValue) {

    public static SchemaChangeResponse from(SchemaChange change) {
        return new SchemaChangeResponse(
                change.path(), change.changeType().name(), change.oldValue(), change.newValue());
    }
}
