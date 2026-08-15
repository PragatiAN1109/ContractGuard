package com.contractguard.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, UUID> {

    List<AnalysisRun> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);
}
