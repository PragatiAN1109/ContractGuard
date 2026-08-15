package com.contractguard.api.schema;

import com.contractguard.schema.SchemaVersion;

import java.time.Instant;
import java.util.UUID;

/** List projection; omits schemaContent so listings stay small. */
public record SchemaVersionSummary(
        UUID id,
        UUID projectId,
        int versionNumber,
        String contentHash,
        Instant createdAt) {

    public static SchemaVersionSummary from(SchemaVersion schemaVersion) {
        return new SchemaVersionSummary(
                schemaVersion.getId(),
                schemaVersion.getProject().getId(),
                schemaVersion.getVersionNumber(),
                schemaVersion.getContentHash(),
                schemaVersion.getCreatedAt());
    }
}
