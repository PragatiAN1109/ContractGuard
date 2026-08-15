package com.contractguard.project;

import com.contractguard.shared.ConflictException;
import com.contractguard.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project create(String name, String description) {
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A project named '" + name + "' already exists");
        }
        return projectRepository.save(new Project(name, description));
    }

    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Project getById(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found"));
    }
}
