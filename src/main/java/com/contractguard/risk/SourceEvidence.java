package com.contractguard.risk;

/**
 * Where in consumer source a finding was observed.
 *
 * @param filePath path within the consumer bundle
 * @param fileName file name alone, for display
 * @param line     1-based line number
 * @param snippet  that source line, trimmed
 */
public record SourceEvidence(String filePath, String fileName, int line, String snippet) {
}
