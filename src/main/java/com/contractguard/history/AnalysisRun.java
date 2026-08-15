package com.contractguard.history;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A durable record of one analysis: which versions were compared, when, and what was found.
 *
 * Append-only. Once COMPLETED or FAILED a run is never recomputed or edited; reading history
 * returns exactly what was stored at the time.
 */
@Entity
@Table(name = "analysis_run")
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "source_schema_version_id", nullable = false)
    private UUID sourceSchemaVersionId;

    @Column(name = "target_schema_version_id", nullable = false)
    private UUID targetSchemaVersionId;

    @Column(name = "source_version_number", nullable = false)
    private int sourceVersionNumber;

    @Column(name = "target_version_number", nullable = false)
    private int targetVersionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnalysisStatus status;

    // Summary columns, written once when the run completes, so history listings need no joins.
    @Column(name = "backward_status", length = 8)
    private String backwardStatus;

    @Column(name = "forward_status", length = 8)
    private String forwardStatus;

    @Column(name = "full_status", length = 8)
    private String fullStatus;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "highest_severity", nullable = false, length = 8)
    private String highestSeverity = "NONE";

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    @OrderBy("position ASC")
    private List<AnalysisCompatibilityResult> compatibilityResults = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    @OrderBy("position ASC")
    private List<AnalysisRiskFinding> findings = new ArrayList<>();

    protected AnalysisRun() {
    }

    public AnalysisRun(UUID projectId, UUID sourceSchemaVersionId, UUID targetSchemaVersionId,
                       int sourceVersionNumber, int targetVersionNumber) {
        this.projectId = projectId;
        this.sourceSchemaVersionId = sourceSchemaVersionId;
        this.targetSchemaVersionId = targetSchemaVersionId;
        this.sourceVersionNumber = sourceVersionNumber;
        this.targetVersionNumber = targetVersionNumber;
        this.status = AnalysisStatus.PENDING;
        this.createdAt = Instant.now();
    }

    void markRunning() {
        requireStatus(AnalysisStatus.PENDING);
        this.status = AnalysisStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    void markCompleted(String backwardStatus, String forwardStatus, String fullStatus,
                       int findingCount, String highestSeverity) {
        requireStatus(AnalysisStatus.RUNNING);
        this.status = AnalysisStatus.COMPLETED;
        this.backwardStatus = backwardStatus;
        this.forwardStatus = forwardStatus;
        this.fullStatus = fullStatus;
        this.findingCount = findingCount;
        this.highestSeverity = highestSeverity;
        this.completedAt = Instant.now();
    }

    void markFailed(String failureCode, String failureMessage) {
        this.status = AnalysisStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.completedAt = Instant.now();
    }

    void addCompatibilityResult(AnalysisCompatibilityResult result) {
        compatibilityResults.add(result);
    }

    void addFinding(AnalysisRiskFinding finding) {
        findings.add(finding);
    }

    private void requireStatus(AnalysisStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot move an analysis from " + status + "; expected " + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getSourceSchemaVersionId() {
        return sourceSchemaVersionId;
    }

    public UUID getTargetSchemaVersionId() {
        return targetSchemaVersionId;
    }

    public int getSourceVersionNumber() {
        return sourceVersionNumber;
    }

    public int getTargetVersionNumber() {
        return targetVersionNumber;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getBackwardStatus() {
        return backwardStatus;
    }

    public String getForwardStatus() {
        return forwardStatus;
    }

    public String getFullStatus() {
        return fullStatus;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public String getHighestSeverity() {
        return highestSeverity;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<AnalysisCompatibilityResult> getCompatibilityResults() {
        return List.copyOf(compatibilityResults);
    }

    public List<AnalysisRiskFinding> getFindings() {
        return List.copyOf(findings);
    }
}
