package com.contractguard.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One persisted compatibility incompatibility. */
@Entity
@Table(name = "analysis_compatibility_issue")
public class AnalysisCompatibilityIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "issue_type", nullable = false, length = 64)
    private String issueType;

    @Column(length = 512)
    private String path;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private int position;

    protected AnalysisCompatibilityIssue() {
    }

    public AnalysisCompatibilityIssue(String issueType, String path, String reason, int position) {
        this.issueType = issueType;
        this.path = path;
        this.reason = reason;
        this.position = position;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getPath() {
        return path;
    }

    public String getReason() {
        return reason;
    }

    public int getPosition() {
        return position;
    }
}
