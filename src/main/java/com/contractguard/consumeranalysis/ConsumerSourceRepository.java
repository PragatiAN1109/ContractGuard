package com.contractguard.consumeranalysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsumerSourceRepository extends JpaRepository<ConsumerSource, UUID> {

    /** Active revisions only, so a superseded upload never takes part in a new analysis. */
    List<ConsumerSource> findByProjectIdAndConsumesSchemaAndSupersededAtIsNullOrderByServiceNameAsc(
            UUID projectId, String consumesSchema);

    List<ConsumerSource> findByProjectIdAndSupersededAtIsNullOrderByServiceNameAsc(UUID projectId);

    Optional<ConsumerSource> findByProjectIdAndServiceNameAndSupersededAtIsNull(
            UUID projectId, String serviceName);

    Optional<ConsumerSource> findByIdAndProjectId(UUID id, UUID projectId);
}
