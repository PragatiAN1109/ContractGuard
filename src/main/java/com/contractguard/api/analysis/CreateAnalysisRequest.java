package com.contractguard.api.analysis;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAnalysisRequest(@NotNull UUID sourceSchemaVersionId,
                                    @NotNull UUID targetSchemaVersionId) {
}
