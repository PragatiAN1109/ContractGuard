package com.contractguard.schema;

import com.contractguard.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One stored Avro schema, versioned within its project. */
@Entity
@Table(name = "schema_version")
public class SchemaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** The schema exactly as submitted. */
    @Column(name = "schema_content", nullable = false, columnDefinition = "text")
    private String schemaContent;

    /** SHA-256 of the normalized schema; the duplicate-detection key. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SchemaVersion() {
        // for JPA
    }

    public SchemaVersion(Project project, int versionNumber, String schemaContent, String contentHash) {
        this.project = project;
        this.versionNumber = versionNumber;
        this.schemaContent = schemaContent;
        this.contentHash = contentHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getSchemaContent() {
        return schemaContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
