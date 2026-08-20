package com.contractguard.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A consumer examined by one analysis run. */
@Entity
@Table(name = "analysis_analysed_consumer")
public class AnalysisAnalysedConsumer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 200)
    private String consumerName;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_files", nullable = false, columnDefinition = "text")
    private String sourceFiles = "";

    @Column(nullable = false)
    private int position;

    protected AnalysisAnalysedConsumer() {
    }

    public AnalysisAnalysedConsumer(String consumerName, String sourceType,
                                    List<String> sourceFiles, int position) {
        this.consumerName = consumerName;
        this.sourceType = sourceType;
        this.sourceFiles = String.join("\n", sourceFiles);
        this.position = position;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public List<String> getSourceFiles() {
        return sourceFiles.isBlank() ? List.of() : Arrays.asList(sourceFiles.split("\n"));
    }

    public int getPosition() {
        return position;
    }
}
