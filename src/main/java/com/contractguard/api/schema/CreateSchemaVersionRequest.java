package com.contractguard.api.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSchemaVersionRequest(

        @NotBlank
        @Size(max = 262144, message = "schema content must not exceed 256 KB")
        String schemaContent) {
}
