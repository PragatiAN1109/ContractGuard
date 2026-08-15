package com.contractguard.history;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persisted operational-risk finding. Independent of the compatibility results on the same run. */
@Entity
@Table(name = "analysis_risk_finding")
public class AnalysisRiskFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(nullable = false, length = 8)
    private String severity;

    @Column(nullable = false, length = 200)
    private String consumer;

    @Column(name = "schema_path", nullable = false, length = 512)
    private String schemaPath;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private int position;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "finding_id", nullable = false)
    @OrderBy("position ASC")
    private List<AnalysisFindingAttribute> attributes = new ArrayList<>();

    @OneToOne(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
    private AnalysisSourceEvidence evidence;

    protected AnalysisRiskFinding() {
    }

    public AnalysisRiskFinding(String ruleId, String severity, String consumer, String schemaPath,
                               String reason, int position) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.consumer = consumer;
        this.schemaPath = schemaPath;
        this.reason = reason;
        this.position = position;
    }

    public void addAttribute(AnalysisFindingAttribute attribute) {
        attributes.add(attribute);
    }

    public void setEvidence(AnalysisSourceEvidence evidence) {
        this.evidence = evidence;
        evidence.setFinding(this);
    }

    public UUID getId() {
        return id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getSchemaPath() {
        return schemaPath;
    }

    public String getReason() {
        return reason;
    }

    public int getPosition() {
        return position;
    }

    /** Insertion-ordered, so JSON output stays deterministic. */
    public Map<String, String> getAttributes() {
        Map<String, String> ordered = new LinkedHashMap<>();
        attributes.forEach(attribute -> ordered.put(attribute.getAttributeKey(), attribute.getAttributeValue()));
        return ordered;
    }

    public AnalysisSourceEvidence getEvidence() {
        return evidence;
    }
}
