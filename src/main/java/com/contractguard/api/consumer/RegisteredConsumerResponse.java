package com.contractguard.api.consumer;

import com.contractguard.consumeranalysis.ConsumerSource;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A registered consumer-source revision. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegisteredConsumerResponse(UUID id,
                                         UUID projectId,
                                         String serviceName,
                                         String consumesSchema,
                                         String sourceType,
                                         String revision,
                                         String revisionHash,
                                         int fileCount,
                                         String description,
                                         List<String> sourceFiles,
                                         Instant createdAt,
                                         Instant supersededAt) {

    public static RegisteredConsumerResponse from(ConsumerSource source) {
        return new RegisteredConsumerResponse(
                source.getId(),
                source.getProjectId(),
                source.getServiceName(),
                source.getConsumesSchema(),
                source.getSourceType(),
                source.shortRevision(),
                source.getRevisionHash(),
                source.getFileCount(),
                source.getDescription(),
                source.getFilePaths(),
                source.getCreatedAt(),
                source.getSupersededAt());
    }
}
