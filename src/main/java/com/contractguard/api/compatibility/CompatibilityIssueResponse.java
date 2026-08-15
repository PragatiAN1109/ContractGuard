package com.contractguard.api.compatibility;

import com.contractguard.compatibility.CompatibilityIssue;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Null path is serialized explicitly, so "Avro reported no location" is visible rather than absent. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CompatibilityIssueResponse(String issueType, String path, String reason) {

    public static CompatibilityIssueResponse from(CompatibilityIssue issue) {
        return new CompatibilityIssueResponse(issue.issueType().name(), issue.path(), issue.reason());
    }
}
