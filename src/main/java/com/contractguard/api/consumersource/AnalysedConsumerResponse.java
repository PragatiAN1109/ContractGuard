package com.contractguard.api.consumersource;

import com.contractguard.consumeranalysis.AnalysedConsumer;

import java.util.List;

/** A consumer whose source took part in an analysis. */
public record AnalysedConsumerResponse(String name, String sourceType, List<String> sourceFiles) {

    public static AnalysedConsumerResponse from(AnalysedConsumer consumer) {
        return new AnalysedConsumerResponse(
                consumer.name(), consumer.sourceType().name(), consumer.sourceFiles());
    }
}
