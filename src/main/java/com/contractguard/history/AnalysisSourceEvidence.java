package com.contractguard.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Persisted source location backing a finding. */
@Entity
@Table(name = "analysis_source_evidence")
public class AnalysisSourceEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "line_number", nullable = false)
    private int line;

    @Column(nullable = false, columnDefinition = "text")
    private String snippet;

    @OneToOne
    @JoinColumn(name = "finding_id", nullable = false)
    private AnalysisRiskFinding finding;

    protected AnalysisSourceEvidence() {
    }

    public AnalysisSourceEvidence(String filePath, String fileName, int line, String snippet) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.line = line;
        this.snippet = snippet;
    }

    public UUID getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLine() {
        return line;
    }

    public String getSnippet() {
        return snippet;
    }

    void setFinding(AnalysisRiskFinding finding) {
        this.finding = finding;
    }
}
