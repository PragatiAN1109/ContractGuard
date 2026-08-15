package com.contractguard.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByNameIgnoreCase(String name);

    List<Project> findAllByOrderByCreatedAtDesc();
}
