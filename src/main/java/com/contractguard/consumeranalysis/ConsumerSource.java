package com.contractguard.consumeranalysis;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One immutable revision of a service's registered Java source.
 *
 * Registering the same service again creates a new revision and supersedes this one, so an analysis
 * can always name the exact revision it read.
 */
@Entity
@Table(name = "consumer_source")
public class ConsumerSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "consumes_schema", nullable = false, length = 512)
    private String consumesSchema;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "revision_hash", nullable = false, length = 64)
    private String revisionHash;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "consumer_source_id", nullable = false)
    @OrderBy("position ASC")
    private List<ConsumerSourceFileEntity> files = new ArrayList<>();

    protected ConsumerSource() {
    }

    public ConsumerSource(UUID projectId, String serviceName, String consumesSchema,
                          ConsumerSourceType sourceType, String description, JavaSourceBundle bundle) {
        this.projectId = projectId;
        this.serviceName = serviceName;
        this.consumesSchema = consumesSchema;
        this.sourceType = sourceType.name();
        this.description = description;
        this.revisionHash = bundle.revisionHash();
        this.fileCount = bundle.files().size();
        this.createdAt = Instant.now();

        int position = 0;
        for (ConsumerSourceFile file : bundle.files()) {
            files.add(new ConsumerSourceFileEntity(file.path(), file.content(), position++));
        }
    }

    void supersede() {
        this.supersededAt = Instant.now();
    }

    /** Short form used in the UI and in provenance labels. */
    public String shortRevision() {
        return revisionHash.substring(0, Math.min(12, revisionHash.length()));
    }

    ConsumerDefinition toDefinition() {
        return new ConsumerDefinition(
                serviceName, description, consumesSchema,
                ConsumerSourceType.valueOf(sourceType), id, revisionHash,
                files.stream().map(f -> new ConsumerSourceFile(f.getPath(), f.getContent())).toList());
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getConsumesSchema() {
        return consumesSchema;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getRevisionHash() {
        return revisionHash;
    }

    public int getFileCount() {
        return fileCount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public List<String> getFilePaths() {
        return files.stream().map(ConsumerSourceFileEntity::getPath).toList();
    }
}
