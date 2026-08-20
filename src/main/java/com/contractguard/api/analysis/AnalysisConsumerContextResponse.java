package com.contractguard.api.analysis;

import com.contractguard.history.AnalysisRun;

import java.util.List;

/**
 * Where the operational-risk analysis got its consumer source.
 *
 * Read from the run's own snapshot, not the live registry, so history stays truthful even if the
 * registry changes afterwards.
 */
public record AnalysisConsumerContextResponse(int consumerCount,
                                              List<String> sourceTypes,
                                              List<AnalysedConsumerSnapshot> consumers) {

    public record AnalysedConsumerSnapshot(String name, String sourceType, String consumerSourceId,
                                          String revision, List<String> sourceFiles) {}

    static AnalysisConsumerContextResponse from(AnalysisRun run) {
        List<AnalysedConsumerSnapshot> consumers = run.getAnalysedConsumers().stream()
                .map(c -> new AnalysedConsumerSnapshot(
                        c.getConsumerName(), c.getSourceType(),
                        c.getConsumerSourceId() == null ? null : c.getConsumerSourceId().toString(),
                        c.getShortRevision(), c.getSourceFiles()))
                .toList();
        return new AnalysisConsumerContextResponse(
                consumers.size(),
                consumers.stream().map(AnalysedConsumerSnapshot::sourceType).distinct().sorted().toList(),
                consumers);
    }
}
