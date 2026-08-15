package com.contractguard.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroSchemaValidatorTest {

    private static final String ORDER_EVENT = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.orders",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "status", "type": {
                  "type": "enum", "name": "OrderStatus",
                  "symbols": ["CREATED", "PAID", "SHIPPED"]
                }}
              ]
            }
            """;

    private final AvroSchemaValidator validator = new AvroSchemaValidator();

    @Test
    @DisplayName("accepts a valid record schema and returns a 64-character hex hash")
    void acceptsValidSchema() {
        AvroSchemaValidator.NormalizedSchema result = validator.validate(ORDER_EVENT);

        assertThat(result.normalizedContent()).contains("OrderEvent");
        assertThat(result.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("hash is stable across repeated calls")
    void hashIsDeterministic() {
        assertThat(validator.validate(ORDER_EVENT).contentHash())
                .isEqualTo(validator.validate(ORDER_EVENT).contentHash());
    }

    @Test
    @DisplayName("formatting and field key order do not change the hash")
    void hashIgnoresFormatting() {
        String reformatted = """
            {"namespace":"com.example.orders","name":"OrderEvent","type":"record","fields":[
              {"type":"string","name":"orderId"},
              {"name":"status","type":{"name":"OrderStatus","type":"enum",
                 "symbols":["CREATED","PAID","SHIPPED"]}}]}
            """;

        assertThat(validator.validate(reformatted).contentHash())
                .isEqualTo(validator.validate(ORDER_EVENT).contentHash());
    }

    @Test
    @DisplayName("a schema differing only in a field default gets a different hash")
    void defaultValueChangesHash() {
        String withDefault = ORDER_EVENT.replace(
                "{\"name\": \"orderId\", \"type\": \"string\"}",
                "{\"name\": \"orderId\", \"type\": \"string\", \"default\": \"\"}");

        assertThat(validator.validate(withDefault).contentHash())
                .isNotEqualTo(validator.validate(ORDER_EVENT).contentHash());
    }

    @Test
    @DisplayName("adding an enum symbol changes the hash")
    void enumSymbolChangesHash() {
        String withExtraSymbol = ORDER_EVENT.replace("\"SHIPPED\"", "\"SHIPPED\", \"DELIVERED\"");

        assertThat(validator.validate(withExtraSymbol).contentHash())
                .isNotEqualTo(validator.validate(ORDER_EVENT).contentHash());
    }

    @Test
    @DisplayName("rejects content that is not JSON")
    void rejectsNonJson() {
        assertThatThrownBy(() -> validator.validate("this is not a schema"))
                .isInstanceOf(InvalidAvroSchemaException.class)
                .hasMessageStartingWith("Invalid Avro schema:");
    }

    @Test
    @DisplayName("rejects JSON that is not a valid Avro schema")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> validator.validate("{\"type\": \"nonsense\"}"))
                .isInstanceOf(InvalidAvroSchemaException.class);
    }

    @Test
    @DisplayName("rejects a record whose fields are missing")
    void rejectsRecordWithoutFields() {
        assertThatThrownBy(() -> validator.validate("{\"type\": \"record\", \"name\": \"Broken\"}"))
                .isInstanceOf(InvalidAvroSchemaException.class);
    }

    @Test
    @DisplayName("rejects a field whose default does not match its type")
    void rejectsInvalidDefault() {
        String badDefault = """
                {"type":"record","name":"Broken","fields":[
                  {"name":"count","type":"int","default":"not-a-number"}]}
                """;

        assertThatThrownBy(() -> validator.validate(badDefault))
                .isInstanceOf(InvalidAvroSchemaException.class);
    }

    @Test
    @DisplayName("rejects a primitive root type")
    void rejectsPrimitiveRoot() {
        assertThatThrownBy(() -> validator.validate("\"string\""))
                .isInstanceOf(InvalidAvroSchemaException.class)
                .hasMessage("Invalid Avro schema: the root type must be a record, but was string");
    }

    @Test
    @DisplayName("rejects an enum root type")
    void rejectsEnumRoot() {
        assertThatThrownBy(() -> validator.validate(
                "{\"type\":\"enum\",\"name\":\"OrderStatus\",\"symbols\":[\"CREATED\"]}"))
                .isInstanceOf(InvalidAvroSchemaException.class)
                .hasMessageContaining("must be a record, but was enum");
    }

    @Test
    @DisplayName("rejects an array root type")
    void rejectsArrayRoot() {
        assertThatThrownBy(() -> validator.validate("{\"type\":\"array\",\"items\":\"string\"}"))
                .isInstanceOf(InvalidAvroSchemaException.class)
                .hasMessageContaining("must be a record, but was array");
    }

    @Test
    @DisplayName("rejects a union root type")
    void rejectsUnionRoot() {
        assertThatThrownBy(() -> validator.validate("[\"null\",\"string\"]"))
                .isInstanceOf(InvalidAvroSchemaException.class)
                .hasMessageContaining("must be a record, but was union");
    }

    @Test
    @DisplayName("non-record types are still allowed below the root")
    void allowsNonRecordTypesBelowTheRoot() {
        String nested = """
                {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                  {"name":"tags","type":{"type":"array","items":"string"}},
                  {"name":"status","type":{"type":"enum","name":"OrderStatus","symbols":["CREATED"]}}]}
                """;

        assertThat(validator.validate(nested).contentHash()).hasSize(64);
    }

    @Test
    @DisplayName("a type defined in one schema is not visible to the next")
    void parserStateIsNotSharedBetweenCalls() {
        validator.validate(ORDER_EVENT);

        // OrderStatus was defined above; referencing it here must fail.
        String referencesPreviousType = """
                {"type":"record","name":"Other","namespace":"com.example.orders","fields":[
                  {"name":"status","type":"OrderStatus"}]}
                """;

        assertThatThrownBy(() -> validator.validate(referencesPreviousType))
                .isInstanceOf(InvalidAvroSchemaException.class);
    }
}
