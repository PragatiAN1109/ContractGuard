package com.contractguard.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 1000)
        String description) {
}
