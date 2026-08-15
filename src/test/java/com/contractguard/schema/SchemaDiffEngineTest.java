package com.contractguard.schema;

import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDiffEngineTest {

    private final SchemaDiffEngine engine = new SchemaDiffEngine();

    /** Builds a one-record schema around the supplied field declarations. */
    private static Schema record(String fields) {
        return new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example.orders\","
                        + "\"fields\":[" + fields + "]}");
    }

    private List<SchemaChange> diffFields(String sourceFields, String targetFields) {
        return engine.diff(record(sourceFields), record(targetFields));
    }

    private static final String ORDER_ID = "{\"name\":\"orderId\",\"type\":\"string\"}";

    @Nested
    @DisplayName("field changes")
    class FieldChanges {

        @Test
        void fieldAdded() {
            List<SchemaChange> changes = diffFields(
                    ORDER_ID,
                    ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\"}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.channel", SchemaChangeType.FIELD_ADDED, null, "string"));
        }

        @Test
        void fieldRemoved() {
            List<SchemaChange> changes = diffFields(
                    ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\"}",
                    ORDER_ID);

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.channel", SchemaChangeType.FIELD_REMOVED, "string", null));
        }

        @Test
        void fieldTypeChanged() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"totalCents\",\"type\":\"int\"}",
                    "{\"name\":\"totalCents\",\"type\":\"long\"}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.totalCents", SchemaChangeType.FIELD_TYPE_CHANGED, "int", "long"));
        }

        @Test
        @DisplayName("type change inside a nullable union is reported without an optionality change")
        void fieldTypeChangedInsideUnion() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"code\",\"type\":[\"null\",\"string\"],\"default\":null}",
                    "{\"name\":\"code\",\"type\":[\"null\",\"int\"],\"default\":null}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.code", SchemaChangeType.FIELD_TYPE_CHANGED, "string", "int"));
        }
    }

    @Nested
    @DisplayName("optionality")
    class Optionality {

        @Test
        @DisplayName("required to optional is an optionality change, not a type change")
        void requiredBecomesOptional() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"customerEmail\",\"type\":\"string\"}",
                    "{\"name\":\"customerEmail\",\"type\":[\"null\",\"string\"],\"default\":null}");

            assertThat(changes).contains(new SchemaChange(
                    "OrderEvent.customerEmail", SchemaChangeType.FIELD_OPTIONALITY_CHANGED,
                    "REQUIRED", "OPTIONAL"));
            assertThat(changes).noneMatch(c -> c.changeType() == SchemaChangeType.FIELD_TYPE_CHANGED);
        }

        @Test
        void optionalBecomesRequired() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"couponCode\",\"type\":[\"null\",\"string\"],\"default\":null}",
                    "{\"name\":\"couponCode\",\"type\":\"string\"}");

            assertThat(changes).contains(new SchemaChange(
                    "OrderEvent.couponCode", SchemaChangeType.FIELD_OPTIONALITY_CHANGED,
                    "OPTIONAL", "REQUIRED"));
        }
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        void defaultValueChanged() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"USD\"}",
                    "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"UNSPECIFIED\"}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.currency", SchemaChangeType.DEFAULT_VALUE_CHANGED, "USD", "UNSPECIFIED"));
        }

        @Test
        @DisplayName("adding a default reports a null old value")
        void defaultAdded() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"currency\",\"type\":\"string\"}",
                    "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"USD\"}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.currency", SchemaChangeType.DEFAULT_VALUE_CHANGED, null, "USD"));
        }

        @Test
        void defaultRemoved() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"USD\"}",
                    "{\"name\":\"currency\",\"type\":\"string\"}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.currency", SchemaChangeType.DEFAULT_VALUE_CHANGED, "USD", null));
        }

        @Test
        @DisplayName("no default and an explicit null default are different")
        void absentDefaultDiffersFromNullDefault() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"couponCode\",\"type\":[\"null\",\"string\"]}",
                    "{\"name\":\"couponCode\",\"type\":[\"null\",\"string\"],\"default\":null}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.couponCode", SchemaChangeType.DEFAULT_VALUE_CHANGED, null, "null"));
        }
    }

    @Nested
    @DisplayName("enums")
    class Enums {

        private static final String STATUS_ENUM =
                "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"OrderStatus\","
                        + "\"symbols\":[%s]}}";

        @Test
        void enumSymbolAdded() {
            List<SchemaChange> changes = diffFields(
                    STATUS_ENUM.formatted("\"CREATED\",\"PAID\""),
                    STATUS_ENUM.formatted("\"CREATED\",\"PAID\",\"RETURNED\""));

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_ADDED, null, "RETURNED"));
        }

        @Test
        void enumSymbolRemoved() {
            List<SchemaChange> changes = diffFields(
                    STATUS_ENUM.formatted("\"CREATED\",\"PAID\",\"RETURNED\""),
                    STATUS_ENUM.formatted("\"CREATED\",\"PAID\""));

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_REMOVED, "RETURNED", null));
        }

        @Test
        @DisplayName("several symbol changes are all reported")
        void multipleSymbolChanges() {
            List<SchemaChange> changes = diffFields(
                    STATUS_ENUM.formatted("\"CREATED\",\"PAID\""),
                    STATUS_ENUM.formatted("\"CREATED\",\"RETURNED\",\"REFUNDED\""));

            assertThat(changes).containsExactly(
                    new SchemaChange("OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_ADDED, null, "REFUNDED"),
                    new SchemaChange("OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_ADDED, null, "RETURNED"),
                    new SchemaChange("OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_REMOVED, "PAID", null));
        }
    }

    @Nested
    @DisplayName("named types")
    class NamedTypes {

        @Test
        void recordNameChanged() {
            Schema source = new Schema.Parser().parse(
                    "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example.orders\","
                            + "\"fields\":[" + ORDER_ID + "]}");
            Schema target = new Schema.Parser().parse(
                    "{\"type\":\"record\",\"name\":\"OrderLifecycleEvent\",\"namespace\":\"com.example.orders\","
                            + "\"fields\":[" + ORDER_ID + "]}");

            assertThat(engine.diff(source, target)).containsExactly(new SchemaChange(
                    "OrderEvent", SchemaChangeType.RECORD_NAME_CHANGED, "OrderEvent", "OrderLifecycleEvent"));
        }

        @Test
        void namespaceChanged() {
            Schema source = new Schema.Parser().parse(
                    "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example.orders\","
                            + "\"fields\":[" + ORDER_ID + "]}");
            Schema target = new Schema.Parser().parse(
                    "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example.commerce\","
                            + "\"fields\":[" + ORDER_ID + "]}");

            assertThat(engine.diff(source, target)).containsExactly(new SchemaChange(
                    "OrderEvent", SchemaChangeType.NAMESPACE_CHANGED,
                    "com.example.orders", "com.example.commerce"));
        }

        @Test
        @DisplayName("a renamed enum is reported once, not also as a type change")
        void renamedEnumIsNotAlsoATypeChange() {
            List<SchemaChange> changes = diffFields(
                    "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"OrderStatus\",\"symbols\":[\"A\"]}}",
                    "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"LifecycleStatus\",\"symbols\":[\"A\"]}}");

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.status", SchemaChangeType.RECORD_NAME_CHANGED, "OrderStatus", "LifecycleStatus"));
        }
    }

    @Nested
    @DisplayName("nesting")
    class Nesting {

        private static final String ITEMS =
                "{\"name\":\"items\",\"type\":{\"type\":\"array\",\"items\":"
                        + "{\"type\":\"record\",\"name\":\"OrderLine\",\"fields\":[%s]}}}";

        @Test
        @DisplayName("changes inside array elements use a [] path segment")
        void nestedFieldInsideArray() {
            List<SchemaChange> changes = diffFields(
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"}"),
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"},"
                            + "{\"name\":\"discountCents\",\"type\":[\"null\",\"int\"],\"default\":null}"));

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.items[].discountCents", SchemaChangeType.FIELD_ADDED, null, "union<null,int>"));
        }

        @Test
        @DisplayName("changes inside map values use a {} path segment")
        void nestedFieldInsideMap() {
            String attributes = "{\"name\":\"attributes\",\"type\":{\"type\":\"map\",\"values\":"
                    + "{\"type\":\"record\",\"name\":\"Attribute\",\"fields\":[%s]}}}";

            List<SchemaChange> changes = diffFields(
                    attributes.formatted("{\"name\":\"value\",\"type\":\"string\"}"),
                    attributes.formatted("{\"name\":\"value\",\"type\":\"string\"},"
                            + "{\"name\":\"unit\",\"type\":\"string\"}"));

            assertThat(changes).containsExactly(new SchemaChange(
                    "OrderEvent.attributes{}.unit", SchemaChangeType.FIELD_ADDED, null, "string"));
        }

        @Test
        @DisplayName("a self-referencing schema terminates instead of recursing forever")
        void recursiveSchemaTerminates() {
            String node = "{\"type\":\"record\",\"name\":\"Node\",\"namespace\":\"com.example\",\"fields\":["
                    + "{\"name\":\"value\",\"type\":\"%s\"},"
                    + "{\"name\":\"next\",\"type\":[\"null\",\"Node\"],\"default\":null}]}";

            List<SchemaChange> changes = engine.diff(
                    new Schema.Parser().parse(node.formatted("string")),
                    new Schema.Parser().parse(node.formatted("int")));

            assertThat(changes).containsExactly(new SchemaChange(
                    "Node.value", SchemaChangeType.FIELD_TYPE_CHANGED, "string", "int"));
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        void identicalSchemasProduceNoChanges() {
            assertThat(diffFields(ORDER_ID, ORDER_ID)).isEmpty();
        }

        @Test
        @DisplayName("repeated runs produce the same changes in the same order")
        void repeatedRunsAreIdentical() {
            String source = ORDER_ID + ",{\"name\":\"currency\",\"type\":\"string\",\"default\":\"USD\"}";
            String target = "{\"name\":\"channel\",\"type\":\"string\"},"
                    + "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"EUR\"},"
                    + "{\"name\":\"orderId\",\"type\":\"int\"}";

            List<SchemaChange> first = diffFields(source, target);
            for (int run = 0; run < 20; run++) {
                assertThat(diffFields(source, target)).isEqualTo(first);
            }
            assertThat(first).hasSize(3);
        }

        @Test
        @DisplayName("field declaration order does not affect the result")
        void fieldOrderDoesNotAffectResult() {
            String a = "{\"name\":\"a\",\"type\":\"string\"}";
            String b = "{\"name\":\"b\",\"type\":\"string\"}";
            String added = "{\"name\":\"c\",\"type\":\"string\"}";

            assertThat(diffFields(a + "," + b, a + "," + b + "," + added))
                    .isEqualTo(diffFields(b + "," + a, added + "," + b + "," + a));
        }
    }

    @Nested
    @DisplayName("the built-in e-commerce order sample")
    class OrderSample {

        @Test
        @DisplayName("v1 to v2 reports exactly the documented changes")
        void sampleProducesDocumentedChanges() throws Exception {
            Schema v1 = new Schema.Parser().parse(readSample("order-v1.avsc"));
            Schema v2 = new Schema.Parser().parse(readSample("order-v2.avsc"));

            assertThat(engine.diff(v1, v2)).containsExactly(
                    new SchemaChange("OrderEvent.channel", SchemaChangeType.FIELD_ADDED, null, "string"),
                    new SchemaChange("OrderEvent.currency", SchemaChangeType.DEFAULT_VALUE_CHANGED,
                            "USD", "UNSPECIFIED"),
                    new SchemaChange("OrderEvent.customerEmail", SchemaChangeType.DEFAULT_VALUE_CHANGED,
                            null, "null"),
                    new SchemaChange("OrderEvent.customerEmail", SchemaChangeType.FIELD_OPTIONALITY_CHANGED,
                            "REQUIRED", "OPTIONAL"),
                    new SchemaChange("OrderEvent.items[].discountCents", SchemaChangeType.FIELD_ADDED,
                            null, "union<null,int>"),
                    new SchemaChange("OrderEvent.status", SchemaChangeType.ENUM_SYMBOL_ADDED,
                            null, "RETURNED"));
        }

        @Test
        @DisplayName("comparing v2 back to v1 is the mirror image")
        void reverseDirection() {
            // Guards against a diff that only looks at one side.
            assertThat(engine.diff(
                    new Schema.Parser().parse(readSample("order-v2.avsc")),
                    new Schema.Parser().parse(readSample("order-v1.avsc"))))
                    .extracting(SchemaChange::changeType)
                    .containsExactly(
                            SchemaChangeType.FIELD_REMOVED,
                            SchemaChangeType.DEFAULT_VALUE_CHANGED,
                            SchemaChangeType.DEFAULT_VALUE_CHANGED,
                            SchemaChangeType.FIELD_OPTIONALITY_CHANGED,
                            SchemaChangeType.FIELD_REMOVED,
                            SchemaChangeType.ENUM_SYMBOL_REMOVED);
        }
    }

    static String readSample(String fileName) {
        try (var stream = SchemaDiffEngineTest.class.getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing from the classpath", e);
        }
    }
}
