package com.contractguard.consumeranalysis;

import java.util.List;

/**
 * A consumer that was examined during an analysis, whether or not it produced findings.
 *
 * Recorded so a clean risk result stays interpretable: "no findings" only means something if you
 * know what was looked at.
 */
public record AnalysedConsumer(String name,
                               ConsumerSourceType sourceType,
                               java.util.UUID sourceId,
                               String revisionHash,
                               List<String> sourceFiles) {

    public AnalysedConsumer {
        sourceFiles = List.copyOf(sourceFiles);
    }

    static AnalysedConsumer from(ConsumerDefinition consumer) {
        return new AnalysedConsumer(
                consumer.name(),
                consumer.sourceType(),
                consumer.sourceId(),
                consumer.revisionHash(),
                consumer.sourceFiles().stream().map(ConsumerSourceFile::path).sorted().toList());
    }
}
