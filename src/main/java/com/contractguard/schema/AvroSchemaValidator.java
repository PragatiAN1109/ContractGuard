package com.contractguard.schema;

import org.apache.avro.Schema;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Parses Avro schemas and derives the hash used for duplicate detection. */
@Component
public class AvroSchemaValidator {

    /**
     * @param normalizedContent the schema re-serialized by Avro, so formatting and key order do not matter
     * @param contentHash       SHA-256 of {@code normalizedContent}, lowercase hex
     */
    public record NormalizedSchema(String normalizedContent, String contentHash) {}

    /**
     * @throws InvalidAvroSchemaException if the content is not a parseable Avro schema, or its
     *                                    root type is not a record
     */
    public NormalizedSchema validate(String schemaContent) {
        Schema schema = parse(schemaContent);

        // Avro's own rendering is deterministic and keeps defaults and doc, unlike Parsing
        // Canonical Form, which strips them. Two schemas that differ only in a default value
        // must not collide.
        String normalized = schema.toString();
        return new NormalizedSchema(normalized, sha256Hex(normalized));
    }

    /**
     * Parses a schema and enforces the Phase 1 record-root rule.
     *
     * @throws InvalidAvroSchemaException if parsing fails or the root type is not a record
     */
    public Schema parse(String schemaContent) {
        Schema schema = parseAny(schemaContent);
        if (schema.getType() != Schema.Type.RECORD) {
            // Everything downstream — the diff, and later the compatibility and consumer rules —
            // is expressed in terms of fields, so a non-record root has nothing to analyse.
            throw new InvalidAvroSchemaException(
                    "Invalid Avro schema: the root type must be a record, but was "
                            + schema.getType().getName(), null);
        }
        return schema;
    }

    private Schema parseAny(String schemaContent) {
        // A fresh parser each time: parsers remember named types, so a shared one would let a
        // schema resolve a name that was only defined by an earlier, unrelated submission.
        // Name validation is always on in Avro 1.12; default validation is not.
        Schema.Parser parser = new Schema.Parser();
        parser.setValidateDefaults(true);
        try {
            return parser.parse(schemaContent);
        } catch (RuntimeException e) {
            // Deliberately broad: Avro does not raise AvroRuntimeException consistently. An
            // unresolved type reference, for instance, surfaces as a bare NullPointerException.
            // Parsing is a pure function of user input, so any failure here is bad input.
            throw new InvalidAvroSchemaException(describe(e), e);
        }
    }

    private static String describe(RuntimeException e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "Invalid Avro schema: " + detail;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
