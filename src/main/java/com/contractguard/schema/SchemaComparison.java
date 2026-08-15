package com.contractguard.schema;

import java.util.List;

/** The result of comparing two stored schema versions. */
public record SchemaComparison(SchemaVersion source, SchemaVersion target, List<SchemaChange> changes) {
}
