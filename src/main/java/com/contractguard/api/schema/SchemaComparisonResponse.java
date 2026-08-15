package com.contractguard.api.schema;

import com.contractguard.schema.SchemaComparison;

import java.util.List;
import java.util.UUID;

public record SchemaComparisonResponse(
        UUID projectId,
        SchemaVersionSummary sourceVersion,
        SchemaVersionSummary targetVersion,
        int changeCount,
        List<SchemaChangeResponse> changes) {

    public static SchemaComparisonResponse from(SchemaComparison comparison) {
        List<SchemaChangeResponse> changes = comparison.changes().stream()
                .map(SchemaChangeResponse::from)
                .toList();
        return new SchemaComparisonResponse(
                comparison.source().getProject().getId(),
                SchemaVersionSummary.from(comparison.source()),
                SchemaVersionSummary.from(comparison.target()),
                changes.size(),
                changes);
    }
}
