package com.contractguard.api.schema;

import com.contractguard.schema.SchemaVersion;

import java.time.Instant;
import java.util.UUID;

public record SchemaVersionResponse(
        UUID id,
        UUID projectId,
        int versionNumber,
        String schemaContent,
        String contentHash,
        Instant createdAt) {

    public static SchemaVersionResponse from(SchemaVersion schemaVersion) {
        return new SchemaVersionResponse(
                schemaVersion.getId(),
                schemaVersion.getProject().getId(),
                schemaVersion.getVersionNumber(),
                schemaVersion.getSchemaContent(),
                schemaVersion.getContentHash(),
                schemaVersion.getCreatedAt());
    }
}
