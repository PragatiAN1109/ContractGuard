package com.contractguard.compatibility;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.Incompatibility;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Structural compatibility, delegated to Apache Avro.
 *
 * ContractGuard does not invent compatibility semantics: Avro's resolution rules — including
 * numeric promotion and enum defaults — are the authority. This class only chooses which schema
 * plays the reader for each mode and translates Avro's findings into ContractGuard's own types.
 */
@Component
public class AvroCompatibilityEngine {

    private static final Comparator<CompatibilityIssue> STABLE_ORDER =
            Comparator.comparing((CompatibilityIssue issue) -> String.valueOf(issue.path()))
                    .thenComparing(issue -> issue.issueType().name())
                    .thenComparing(CompatibilityIssue::reason);

    /** BACKWARD: can the target schema read data written with the source schema? */
    public CompatibilityModeResult checkBackward(Schema source, Schema target) {
        return check(CompatibilityMode.BACKWARD, target, source,
                "The target schema can read data written with the source schema.",
                "The target schema cannot read data written with the source schema.");
    }

    /** FORWARD: can the source schema read data written with the target schema? */
    public CompatibilityModeResult checkForward(Schema source, Schema target) {
        return check(CompatibilityMode.FORWARD, source, target,
                "The source schema can read data written with the target schema.",
                "The source schema cannot read data written with the target schema.");
    }

    /**
     * FULL is derived, not re-checked. It carries no issues of its own — the failing direction
     * already lists them, and duplicating them here would obscure which direction each belongs to.
     */
    public CompatibilityModeResult deriveFull(CompatibilityModeResult backward, CompatibilityModeResult forward) {
        if (backward.isPass() && forward.isPass()) {
            return new CompatibilityModeResult(CompatibilityMode.FULL, CompatibilityStatus.PASS,
                    "Both directions are compatible.", List.of());
        }
        String failing = !backward.isPass() && !forward.isPass() ? "BACKWARD and FORWARD"
                : backward.isPass() ? "FORWARD" : "BACKWARD";
        return new CompatibilityModeResult(CompatibilityMode.FULL, CompatibilityStatus.FAIL,
                "FULL requires both directions; " + failing + " failed.", List.of());
    }

    private CompatibilityModeResult check(CompatibilityMode mode, Schema reader, Schema writer,
                                          String passSummary, String failSummary) {
        SchemaPairCompatibility result = SchemaCompatibility.checkReaderWriterCompatibility(reader, writer);

        List<CompatibilityIssue> issues = new ArrayList<>();
        for (Incompatibility incompatibility : result.getResult().getIncompatibilities()) {
            issues.add(toIssue(incompatibility, reader));
        }
        // Avro's traversal order is already stable, but sorting makes determinism a property of
        // this class rather than an assumption about Avro's internals.
        issues.sort(STABLE_ORDER);

        if (issues.isEmpty()) {
            return new CompatibilityModeResult(mode, CompatibilityStatus.PASS, passSummary, List.of());
        }
        return new CompatibilityModeResult(mode, CompatibilityStatus.FAIL,
                failSummary + " " + issues.size() + (issues.size() == 1 ? " incompatibility" : " incompatibilities")
                        + " found.",
                List.copyOf(issues));
    }

    private static CompatibilityIssue toIssue(Incompatibility incompatibility, Schema reader) {
        CompatibilityIssueType type = mapType(incompatibility.getType());
        String path = SchemaLocationResolver.resolve(incompatibility.getLocation(), reader);
        return new CompatibilityIssue(type, path, reason(type, path, incompatibility.getMessage()));
    }

    private static CompatibilityIssueType mapType(SchemaCompatibility.SchemaIncompatibilityType avroType) {
        if (avroType == null) {
            return CompatibilityIssueType.UNKNOWN;
        }
        return switch (avroType) {
            case READER_FIELD_MISSING_DEFAULT_VALUE -> CompatibilityIssueType.READER_FIELD_MISSING_DEFAULT_VALUE;
            case TYPE_MISMATCH -> CompatibilityIssueType.TYPE_MISMATCH;
            case MISSING_ENUM_SYMBOLS -> CompatibilityIssueType.MISSING_ENUM_SYMBOLS;
            case MISSING_UNION_BRANCH -> CompatibilityIssueType.MISSING_UNION_BRANCH;
            case NAME_MISMATCH -> CompatibilityIssueType.NAME_MISMATCH;
            case FIXED_SIZE_MISMATCH -> CompatibilityIssueType.FIXED_SIZE_MISMATCH;
        };
    }

    /**
     * Avro's own message is terse and inconsistent — sometimes a bare field name, sometimes a
     * symbol list, sometimes prose — so each category gets a full sentence built here.
     */
    private static String reason(CompatibilityIssueType type, String path, String avroMessage) {
        String where = path == null ? "the schema root" : path;
        String detail = avroMessage == null ? "" : avroMessage.trim();
        return switch (type) {
            case READER_FIELD_MISSING_DEFAULT_VALUE -> "Field '" + detail + "' at " + where
                    + " is required by the reading schema, is not produced by the writing schema, "
                    + "and declares no default value.";
            case TYPE_MISMATCH -> "The type at " + where + " cannot be resolved between the two schemas: "
                    + detail + ".";
            case MISSING_ENUM_SYMBOLS -> "The enum at " + where
                    + " can be written with symbols the reading schema does not declare " + detail
                    + ", and the reading enum has no default symbol.";
            case MISSING_UNION_BRANCH -> "The union at " + where
                    + " has no branch matching a type the writing schema can produce: " + detail + ".";
            case NAME_MISMATCH -> "The named type at " + where + " does not match: " + detail + ".";
            case FIXED_SIZE_MISMATCH -> "The fixed type at " + where + " declares a different size: "
                    + detail + ".";
            case UNKNOWN -> "Avro reported an incompatibility at " + where + ": " + detail + ".";
        };
    }
}
