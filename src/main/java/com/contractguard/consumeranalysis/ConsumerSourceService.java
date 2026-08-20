package com.contractguard.consumeranalysis;

import com.contractguard.project.ProjectService;
import com.contractguard.shared.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Registers and reads immutable consumer-source revisions. */
@Service
public class ConsumerSourceService {

    private final ConsumerSourceRepository repository;
    private final ProjectService projectService;

    public ConsumerSourceService(ConsumerSourceRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    /**
     * Registers a new revision. Any existing active revision for the same service is superseded
     * rather than overwritten, so analyses that already ran keep their provenance.
     */
    @Transactional
    public ConsumerSource register(UUID projectId, String serviceName, String consumesSchema,
                                  String description, JavaSourceBundle bundle) {
        projectService.getById(projectId);

        repository.findByProjectIdAndServiceNameAndSupersededAtIsNull(projectId, serviceName)
                .ifPresent(previous -> {
                    previous.supersede();
                    // Hibernate flushes inserts before updates, so without this the new row would
                    // collide with the still-active old row on uq_consumer_source_active.
                    repository.flush();
                });

        return repository.save(new ConsumerSource(
                projectId, serviceName, consumesSchema,
                ConsumerSourceType.UPLOADED_SOURCE, description, bundle));
    }

    @Transactional(readOnly = true)
    public List<ConsumerSource> findActive(UUID projectId) {
        projectService.getById(projectId);
        List<ConsumerSource> active =
                repository.findByProjectIdAndSupersededAtIsNullOrderByServiceNameAsc(projectId);
        active.forEach(source -> source.getFilePaths().size());
        return active;
    }

    @Transactional(readOnly = true)
    public ConsumerSource getById(UUID projectId, UUID consumerSourceId) {
        ConsumerSource source = repository.findByIdAndProjectId(consumerSourceId, projectId)
                .orElseThrow(() -> new NotFoundException(
                        "Consumer source " + consumerSourceId + " not found in project " + projectId));
        source.getFilePaths().size();
        return source;
    }
}
