package com.contractguard.history;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persisted verdict for one compatibility mode. */
@Entity
@Table(name = "analysis_compatibility_result")
public class AnalysisCompatibilityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(nullable = false, length = 8)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private int position;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "compatibility_result_id", nullable = false)
    @OrderBy("position ASC")
    private List<AnalysisCompatibilityIssue> issues = new ArrayList<>();

    protected AnalysisCompatibilityResult() {
    }

    public AnalysisCompatibilityResult(String mode, String status, String summary, int position) {
        this.mode = mode;
        this.status = status;
        this.summary = summary;
        this.position = position;
    }

    public void addIssue(AnalysisCompatibilityIssue issue) {
        issues.add(issue);
    }

    public UUID getId() {
        return id;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public int getPosition() {
        return position;
    }

    public List<AnalysisCompatibilityIssue> getIssues() {
        return List.copyOf(issues);
    }
}
