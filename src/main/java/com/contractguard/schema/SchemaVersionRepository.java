package com.contractguard.schema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, UUID> {

    List<SchemaVersion> findByProjectIdOrderByVersionNumberAsc(UUID projectId);

    Optional<SchemaVersion> findByProjectIdAndContentHash(UUID projectId, String contentHash);

    Optional<SchemaVersion> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("select max(s.versionNumber) from SchemaVersion s where s.project.id = :projectId")
    Optional<Integer> findHighestVersionNumber(@Param("projectId") UUID projectId);
}
