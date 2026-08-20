package com.contractguard.api.consumersource;

import java.util.List;

/**
 * What operational-risk analysis would examine for a schema, read from the registry now.
 *
 * This is a pre-flight view. A completed analysis reports what was actually examined at the time,
 * from its own stored snapshot.
 */
public record ConsumerSourcesResponse(String schemaFullName,
                                      int consumerCount,
                                      List<ConsumerSourceResponse> consumers) {
}
