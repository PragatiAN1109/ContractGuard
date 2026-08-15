package com.contractguard.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Rule-specific detail, e.g. newSymbol / fallbackSymbol. Ordered by insertion. */
@Entity
@Table(name = "analysis_finding_attribute")
public class AnalysisFindingAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attribute_key", nullable = false, length = 64)
    private String attributeKey;

    @Column(name = "attribute_value", length = 512)
    private String attributeValue;

    @Column(nullable = false)
    private int position;

    protected AnalysisFindingAttribute() {
    }

    public AnalysisFindingAttribute(String attributeKey, String attributeValue, int position) {
        this.attributeKey = attributeKey;
        this.attributeValue = attributeValue;
        this.position = position;
    }

    public String getAttributeKey() {
        return attributeKey;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public int getPosition() {
        return position;
    }
}
