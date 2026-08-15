package com.contractguard.api.project;

import com.contractguard.project.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String description, Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(), project.getCreatedAt());
    }
}
