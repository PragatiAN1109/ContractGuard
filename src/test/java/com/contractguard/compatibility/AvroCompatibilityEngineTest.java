package com.contractguard.compatibility;

import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AvroCompatibilityEngineTest {

    private final AvroCompatibilityEngine engine = new AvroCompatibilityEngine();

    private static final String ORDER_ID = "{\"name\":\"orderId\",\"type\":\"string\"}";

    private static Schema record(String fields) {
        return new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"OrderEvent\",\"namespace\":\"com.example.orders\","
                        + "\"fields\":[" + fields + "]}");
    }

    private CompatibilityModeResult backward(String sourceFields, String targetFields) {
        return engine.checkBackward(record(sourceFields), record(targetFields));
    }

    private CompatibilityModeResult forward(String sourceFields, String targetFields) {
        return engine.checkForward(record(sourceFields), record(targetFields));
    }

    @Nested
    @DisplayName("adding fields")
    class AddingFields {

        @Test
        @DisplayName("an optional field with a default is compatible in both directions")
        void optionalFieldWithDefaultIsFullyCompatible() {
            String target = ORDER_ID + ",{\"name\":\"channel\",\"type\":[\"null\",\"string\"],\"default\":null}";

            assertThat(backward(ORDER_ID, target).status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(forward(ORDER_ID, target).status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(engine.deriveFull(backward(ORDER_ID, target), forward(ORDER_ID, target)).status())
                    .isEqualTo(CompatibilityStatus.PASS);
        }

        @Test
        @DisplayName("a required field without a default breaks backward compatibility")
        void requiredFieldWithoutDefaultBreaksBackward() {
            String target = ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\"}";

            CompatibilityModeResult result = backward(ORDER_ID, target);

            assertThat(result.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(result.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType())
                        .isEqualTo(CompatibilityIssueType.READER_FIELD_MISSING_DEFAULT_VALUE);
                assertThat(issue.path()).isEqualTo("OrderEvent.channel");
                assertThat(issue.reason()).isEqualTo("Field 'channel' at OrderEvent.channel is required "
                        + "by the reading schema, is not produced by the writing schema, and declares "
                        + "no default value.");
            });
            // The old schema can still read data from the new one: it ignores the extra field.
            assertThat(forward(ORDER_ID, target).status()).isEqualTo(CompatibilityStatus.PASS);
        }

        @Test
        @DisplayName("a required field with a default is backward compatible")
        void requiredFieldWithDefaultIsBackwardCompatible() {
            String target = ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\",\"default\":\"WEB\"}";

            assertThat(backward(ORDER_ID, target).status()).isEqualTo(CompatibilityStatus.PASS);
        }
    }

    @Nested
    @DisplayName("removing fields")
    class RemovingFields {

        @Test
        @DisplayName("removing a field without a default breaks forward compatibility only")
        void removedFieldBreaksForward() {
            String source = ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\"}";

            assertThat(backward(source, ORDER_ID).status()).isEqualTo(CompatibilityStatus.PASS);

            CompatibilityModeResult result = forward(source, ORDER_ID);
            assertThat(result.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(result.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType())
                        .isEqualTo(CompatibilityIssueType.READER_FIELD_MISSING_DEFAULT_VALUE);
                assertThat(issue.path()).isEqualTo("OrderEvent.channel");
            });
        }

        @Test
        @DisplayName("removing a field that had a default is compatible both ways")
        void removedFieldWithDefaultIsFullyCompatible() {
            String source = ORDER_ID + ",{\"name\":\"channel\",\"type\":\"string\",\"default\":\"WEB\"}";

            assertThat(backward(source, ORDER_ID).status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(forward(source, ORDER_ID).status()).isEqualTo(CompatibilityStatus.PASS);
        }
    }

    @Nested
    @DisplayName("type changes")
    class TypeChanges {

        @Test
        @DisplayName("int to long is a promotion Avro accepts backward")
        void numericPromotionIsBackwardCompatible() {
            String source = "{\"name\":\"totalCents\",\"type\":\"int\"}";
            String target = "{\"name\":\"totalCents\",\"type\":\"long\"}";

            assertThat(backward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);
        }

        @Test
        @DisplayName("a promotion does not hold in reverse, so it is not fully compatible")
        void numericPromotionIsNotForwardCompatible() {
            String source = "{\"name\":\"totalCents\",\"type\":\"int\"}";
            String target = "{\"name\":\"totalCents\",\"type\":\"long\"}";

            CompatibilityModeResult forward = forward(source, target);
            assertThat(forward.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(forward.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType()).isEqualTo(CompatibilityIssueType.TYPE_MISMATCH);
                assertThat(issue.path()).isEqualTo("OrderEvent.totalCents");
                assertThat(issue.reason()).contains("INT").contains("LONG");
            });
            assertThat(engine.deriveFull(backward(source, target), forward).status())
                    .isEqualTo(CompatibilityStatus.FAIL);
        }

        @Test
        @DisplayName("string to int is incompatible in both directions")
        void incompatibleTypeChangeFailsBothWays() {
            String source = "{\"name\":\"orderId\",\"type\":\"string\"}";
            String target = "{\"name\":\"orderId\",\"type\":\"int\"}";

            assertThat(backward(source, target).status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(forward(source, target).status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(backward(source, target).issues()).singleElement().satisfies(issue ->
                    assertThat(issue.path()).isEqualTo("OrderEvent.orderId"));
        }

        @Test
        @DisplayName("making a field optional breaks forward compatibility")
        void requiredToOptionalBreaksForward() {
            String source = "{\"name\":\"customerEmail\",\"type\":\"string\"}";
            String target = "{\"name\":\"customerEmail\",\"type\":[\"null\",\"string\"],\"default\":null}";

            assertThat(backward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);

            CompatibilityModeResult forward = forward(source, target);
            assertThat(forward.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(forward.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.path()).isEqualTo("OrderEvent.customerEmail"));
        }
    }

    @Nested
    @DisplayName("enums")
    class Enums {

        private static final String PLAIN =
                "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"OrderStatus\",\"symbols\":[%s]}}";
        private static final String WITH_DEFAULT =
                "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"OrderStatus\","
                        + "\"symbols\":[%s],\"default\":\"CREATED\"}}";

        @Test
        @DisplayName("adding a symbol breaks forward compatibility when the enum has no default")
        void symbolAddedWithoutDefaultBreaksForward() {
            String source = PLAIN.formatted("\"CREATED\",\"PAID\"");
            String target = PLAIN.formatted("\"CREATED\",\"PAID\",\"RETURNED\"");

            assertThat(backward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);

            CompatibilityModeResult forward = forward(source, target);
            assertThat(forward.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(forward.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType()).isEqualTo(CompatibilityIssueType.MISSING_ENUM_SYMBOLS);
                assertThat(issue.path()).isEqualTo("OrderEvent.status");
                assertThat(issue.reason()).contains("RETURNED").contains("no default symbol");
            });
        }

        @Test
        @DisplayName("an enum default makes an added symbol fully compatible — the motivating case")
        void symbolAddedWithDefaultIsFullyCompatible() {
            String source = WITH_DEFAULT.formatted("\"CREATED\",\"PAID\"");
            String target = WITH_DEFAULT.formatted("\"CREATED\",\"PAID\",\"RETURNED\"");

            CompatibilityModeResult backward = backward(source, target);
            CompatibilityModeResult forward = forward(source, target);

            // Structurally clean in every direction. A consumer switch that lacks a RETURNED branch
            // is still a problem, but that is operational risk, analysed separately.
            assertThat(backward.status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(forward.status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(engine.deriveFull(backward, forward).status()).isEqualTo(CompatibilityStatus.PASS);
        }

        @Test
        @DisplayName("removing a symbol breaks backward compatibility when there is no default")
        void symbolRemovedBreaksBackward() {
            String source = PLAIN.formatted("\"CREATED\",\"PAID\",\"RETURNED\"");
            String target = PLAIN.formatted("\"CREATED\",\"PAID\"");

            CompatibilityModeResult backward = backward(source, target);
            assertThat(backward.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(backward.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType()).isEqualTo(CompatibilityIssueType.MISSING_ENUM_SYMBOLS);
                assertThat(issue.path()).isEqualTo("OrderEvent.status");
            });
            assertThat(forward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);
        }
    }

    @Nested
    @DisplayName("nested structures")
    class Nested_ {

        private static final String ITEMS =
                "{\"name\":\"items\",\"type\":{\"type\":\"array\",\"items\":"
                        + "{\"type\":\"record\",\"name\":\"OrderLine\",\"fields\":[%s]}}}";

        @Test
        @DisplayName("an incompatible nested type reports the nested path")
        void nestedTypeMismatchReportsNestedPath() {
            CompatibilityModeResult result = backward(
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"}"),
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"int\"}"));

            assertThat(result.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(result.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.issueType()).isEqualTo(CompatibilityIssueType.TYPE_MISMATCH);
                assertThat(issue.path()).isEqualTo("OrderEvent.items[].sku");
            });
        }

        @Test
        @DisplayName("a required nested field added without a default reports the nested path")
        void nestedRequiredFieldReportsNestedPath() {
            CompatibilityModeResult result = backward(
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"}"),
                    ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"},"
                            + "{\"name\":\"warehouse\",\"type\":\"string\"}"));

            assertThat(result.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.path()).isEqualTo("OrderEvent.items[].warehouse"));
        }

        @Test
        @DisplayName("an optional nested field with a default is fully compatible")
        void nestedOptionalFieldIsFullyCompatible() {
            String source = ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"}");
            String target = ITEMS.formatted("{\"name\":\"sku\",\"type\":\"string\"},"
                    + "{\"name\":\"discountCents\",\"type\":[\"null\",\"int\"],\"default\":null}");

            assertThat(backward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(forward(source, target).status()).isEqualTo(CompatibilityStatus.PASS);
        }

        @Test
        @DisplayName("changes inside map values report a {} path")
        void mapValuePathIsResolved() {
            String attributes = "{\"name\":\"attributes\",\"type\":{\"type\":\"map\",\"values\":"
                    + "{\"type\":\"record\",\"name\":\"Attribute\",\"fields\":[%s]}}}";

            CompatibilityModeResult result = backward(
                    attributes.formatted("{\"name\":\"value\",\"type\":\"string\"}"),
                    attributes.formatted("{\"name\":\"value\",\"type\":\"int\"}"));

            assertThat(result.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.path()).isEqualTo("OrderEvent.attributes{}.value"));
        }
    }

    @Nested
    @DisplayName("report shape")
    class ReportShape {

        @Test
        @DisplayName("a passing check carries no issues")
        void passHasNoIssues() {
            CompatibilityModeResult result = backward(ORDER_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(result.issues()).isEmpty();
            assertThat(result.summary()).isEqualTo(
                    "The target schema can read data written with the source schema.");
        }

        @Test
        @DisplayName("FULL names the direction that failed and carries no issues of its own")
        void fullNamesTheFailingDirection() {
            String source = PLAIN_STATUS.formatted("\"CREATED\"");
            String target = PLAIN_STATUS.formatted("\"CREATED\",\"RETURNED\"");

            CompatibilityModeResult full = engine.deriveFull(backward(source, target), forward(source, target));

            assertThat(full.mode()).isEqualTo(CompatibilityMode.FULL);
            assertThat(full.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(full.summary()).isEqualTo("FULL requires both directions; FORWARD failed.");
            assertThat(full.issues()).isEmpty();
        }

        @Test
        @DisplayName("FULL reports both directions when both fail")
        void fullNamesBothDirections() {
            String source = "{\"name\":\"orderId\",\"type\":\"string\"}";
            String target = "{\"name\":\"orderId\",\"type\":\"int\"}";

            assertThat(engine.deriveFull(backward(source, target), forward(source, target)).summary())
                    .isEqualTo("FULL requires both directions; BACKWARD and FORWARD failed.");
        }

        @Test
        @DisplayName("several incompatibilities are all reported, in a stable order")
        void multipleIssuesAreStablyOrdered() {
            String source = ORDER_ID;
            String target = "{\"name\":\"orderId\",\"type\":\"int\"},"
                    + "{\"name\":\"channel\",\"type\":\"string\"},"
                    + "{\"name\":\"region\",\"type\":\"string\"}";

            CompatibilityModeResult first = backward(source, target);
            assertThat(first.issues()).hasSize(3);
            assertThat(first.issues()).extracting(CompatibilityIssue::path)
                    .containsExactly("OrderEvent.channel", "OrderEvent.orderId", "OrderEvent.region");

            for (int run = 0; run < 20; run++) {
                assertThat(backward(source, target)).isEqualTo(first);
            }
        }

        private static final String PLAIN_STATUS =
                "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"OrderStatus\",\"symbols\":[%s]}}";
    }

    @Nested
    @DisplayName("the built-in e-commerce order sample")
    class OrderSample {

        @Test
        @DisplayName("v1 to v2 is backward compatible but not forward compatible")
        void sampleIsBackwardOnly() {
            Schema v1 = new Schema.Parser().parse(sample("order-v1.avsc"));
            Schema v2 = new Schema.Parser().parse(sample("order-v2.avsc"));

            CompatibilityModeResult backward = engine.checkBackward(v1, v2);
            CompatibilityModeResult forward = engine.checkForward(v1, v2);

            assertThat(backward.status()).isEqualTo(CompatibilityStatus.PASS);
            assertThat(forward.status()).isEqualTo(CompatibilityStatus.FAIL);
            assertThat(engine.deriveFull(backward, forward).status()).isEqualTo(CompatibilityStatus.FAIL);

            // customerEmail became nullable, which the old reader cannot handle.
            assertThat(forward.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.path()).isEqualTo("OrderEvent.customerEmail"));
        }

        @Test
        @DisplayName("the added RETURNED symbol raises no compatibility issue at all")
        void addedEnumSymbolIsStructurallyClean() {
            Schema v1 = new Schema.Parser().parse(sample("order-v1.avsc"));
            Schema v2 = new Schema.Parser().parse(sample("order-v2.avsc"));

            // The sample enum declares a default, so neither direction complains about RETURNED.
            // This is precisely the gap ContractGuard exists to close, via operational risk.
            assertThat(engine.checkBackward(v1, v2).issues())
                    .noneMatch(issue -> issue.path().equals("OrderEvent.status"));
            assertThat(engine.checkForward(v1, v2).issues())
                    .noneMatch(issue -> issue.path().equals("OrderEvent.status"));
        }
    }

    static String sample(String fileName) {
        try (var stream = AvroCompatibilityEngineTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing from the classpath", e);
        }
    }
}
