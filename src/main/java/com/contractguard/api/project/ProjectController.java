package com.contractguard.api.project;

import com.contractguard.project.Project;
import com.contractguard.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        Project project = projectService.create(request.name(), request.description());
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/projects/{id}").build(project.getId()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.getById(projectId));
    }
}
