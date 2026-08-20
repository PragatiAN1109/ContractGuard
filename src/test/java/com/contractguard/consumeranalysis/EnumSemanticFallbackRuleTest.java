package com.contractguard.consumeranalysis;

import com.contractguard.risk.OperationalRiskFinding;
import com.contractguard.risk.RiskRuleId;
import com.contractguard.risk.RiskSeverity;
import com.contractguard.schema.SchemaDiffEngine;
import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnumSemanticFallbackRuleTest {

    private final SchemaDiffEngine diffEngine = new SchemaDiffEngine();
    private final EnumSemanticFallbackRule rule = new EnumSemanticFallbackRule(new JavaEnumUsageScanner());

    // ---- schema fixtures -------------------------------------------------------------------

    private static Schema order(String symbols, String enumDefault) {
        String defaultClause = enumDefault == null ? "" : ",\"default\":\"" + enumDefault + "\"";
        return new Schema.Parser().parse("""
                {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                  {"name":"orderId","type":"string"},
                  {"name":"status","type":{"type":"enum","name":"OrderStatus",
                     "symbols":[%s]%s}}]}
                """.formatted(symbols, defaultClause));
    }

    private static final String V1_SYMBOLS = "\"CREATED\",\"PAID\",\"SHIPPED\",\"CANCELLED\"";
    private static final String V2_SYMBOLS = V1_SYMBOLS + ",\"RETURNED\"";

    // ---- consumer fixtures -----------------------------------------------------------------

    private static ConsumerDefinition consumer(String name, String body) {
        String source = """
                package com.example.notifications;

                import com.example.orders.OrderEvent;
                import com.example.orders.OrderStatus;

                public class Handler {
                    public void handle(OrderEvent order) {
                %s
                    }
                }
                """.formatted(body);
        return new ConsumerDefinition(name, null, "com.example.orders.OrderEvent", ConsumerSourceType.BUILT_IN_SAMPLE,
                List.of(new ConsumerSourceFile(name + "/Handler.java", source)));
    }

    private static final String SWITCH_ON_CREATED = """
                        switch (order.getStatus()) {
                            case CREATED -> sendNewOrderNotification();
                            case SHIPPED -> sendShipmentNotification();
                            case CANCELLED -> sendCancellationNotification();
                        }
            """;

    private List<OperationalRiskFinding> analyse(Schema source, Schema target,
                                                 List<ConsumerDefinition> consumers) {
        return rule.apply(diffEngine.diff(source, target), source, consumers, new ArrayList<>());
    }

    // ---- positive cases --------------------------------------------------------------------

    @Nested
    @DisplayName("the risk is reported")
    class Positive {

        @Test
        @DisplayName("new symbol, old enum has a default, consumer branches on that default")
        void reportsFindingWithFullEvidence() {
            List<OperationalRiskFinding> findings = analyse(
                    order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(consumer("order-notification-service", SWITCH_ON_CREATED)));

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.ruleId()).isEqualTo(RiskRuleId.ENUM_SEMANTIC_FALLBACK_RISK);
                assertThat(finding.severity()).isEqualTo(RiskSeverity.HIGH);
                assertThat(finding.consumer()).isEqualTo("order-notification-service");
                assertThat(finding.schemaPath()).isEqualTo("OrderEvent.status");
                assertThat(finding.attributes())
                        .containsEntry("newSymbol", "RETURNED")
                        .containsEntry("fallbackSymbol", "CREATED")
                        .containsEntry("enumName", "OrderStatus")
                        .containsEntry("usageKind", "SWITCH_CASE");
                assertThat(finding.evidence().fileName()).isEqualTo("Handler.java");
                assertThat(finding.evidence().line()).isEqualTo(9);
                assertThat(finding.evidence().snippet()).isEqualTo("case CREATED -> sendNewOrderNotification();");
                assertThat(finding.reason()).contains("RETURNED").contains("CREATED");
            });
        }

        @Test
        @DisplayName("an equality comparison against the default is also evidence")
        void reportsEqualityComparison() {
            List<OperationalRiskFinding> findings = analyse(
                    order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(consumer("svc", """
                                    if (order.getStatus() == OrderStatus.CREATED) {
                                        sendNewOrderNotification();
                                    }
                            """)));

            assertThat(findings).singleElement().satisfies(finding -> {
                assertThat(finding.attributes()).containsEntry("usageKind", "EQUALITY_COMPARISON");
                assertThat(finding.evidence().snippet())
                        .isEqualTo("if (order.getStatus() == OrderStatus.CREATED) {");
            });
        }

        @Test
        @DisplayName("two added symbols produce one finding each")
        void reportsEachAddedSymbol() {
            List<OperationalRiskFinding> findings = analyse(
                    order(V1_SYMBOLS, "CREATED"),
                    order(V1_SYMBOLS + ",\"RETURNED\",\"REFUNDED\"", "CREATED"),
                    List.of(consumer("svc", SWITCH_ON_CREATED)));

            assertThat(findings).hasSize(2)
                    .extracting(finding -> finding.attributes().get("newSymbol"))
                    .containsExactly("REFUNDED", "RETURNED");
        }

        @Test
        @DisplayName("only the unsafe consumer is flagged among several")
        void flagsOnlyTheUnsafeConsumer() {
            List<OperationalRiskFinding> findings = analyse(
                    order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(
                            consumer("unsafe-service", SWITCH_ON_CREATED),
                            consumer("metrics-service", """
                                            switch (order.getStatus()) {
                                                case SHIPPED -> count();
                                                case CANCELLED -> count();
                                            }
                                    """),
                            consumer("returns-service", """
                                            switch (order.getStatus()) {
                                                case RETURNED -> refund();
                                                case CREATED -> openWindow();
                                            }
                                    """)));

            assertThat(findings).singleElement().satisfies(finding ->
                    assertThat(finding.consumer()).isEqualTo("unsafe-service"));
        }

        @Test
        @DisplayName("a nested enum is reported at its nested path")
        void reportsNestedEnumPath() {
            String nested = """
                    {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                      {"name":"items","type":{"type":"array","items":
                         {"type":"record","name":"OrderLine","fields":[
                            {"name":"lineStatus","type":{"type":"enum","name":"LineStatus",
                               "symbols":[%s],"default":"PENDING"}}]}}}]}
                    """;
            Schema source = new Schema.Parser().parse(nested.formatted("\"PENDING\",\"PICKED\""));
            Schema target = new Schema.Parser().parse(nested.formatted("\"PENDING\",\"PICKED\",\"BACKORDERED\""));

            ConsumerDefinition warehouse = new ConsumerDefinition("warehouse", null,
                    "com.example.orders.OrderEvent", ConsumerSourceType.BUILT_IN_SAMPLE,
                    List.of(new ConsumerSourceFile("warehouse/Picker.java", """
                            import com.example.orders.LineStatus;
                            public class Picker {
                                void pick(Line line) {
                                    if (line.getLineStatus() == LineStatus.PENDING) {
                                        enqueue();
                                    }
                                }
                            }
                            """)));

            assertThat(analyse(source, target, List.of(warehouse)))
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.schemaPath()).isEqualTo("OrderEvent.items[].lineStatus");
                        assertThat(finding.attributes()).containsEntry("fallbackSymbol", "PENDING");
                    });
        }
    }

    // ---- negative cases --------------------------------------------------------------------

    @Nested
    @DisplayName("nothing is reported")
    class Negative {

        @Test
        @DisplayName("the old enum has no default, so nothing silently falls back")
        void noFindingWithoutEnumDefault() {
            assertThat(analyse(order(V1_SYMBOLS, null), order(V2_SYMBOLS, null),
                    List.of(consumer("svc", SWITCH_ON_CREATED)))).isEmpty();
        }

        @Test
        @DisplayName("the consumer never branches on the default symbol")
        void noFindingWhenDefaultIsNotUsed() {
            assertThat(analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(consumer("svc", """
                                    switch (order.getStatus()) {
                                        case SHIPPED -> count();
                                        case CANCELLED -> count();
                                        default -> ignore();
                                    }
                            """)))).isEmpty();
        }

        @Test
        @DisplayName("the consumer already knows the new symbol")
        void noFindingWhenConsumerHandlesNewSymbol() {
            assertThat(analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(consumer("svc", """
                                    switch (order.getStatus()) {
                                        case CREATED -> sendNewOrderNotification();
                                        case RETURNED -> refund();
                                    }
                            """)))).isEmpty();
        }

        @Test
        @DisplayName("no enum symbol was added")
        void noFindingWithoutEnumSymbolAddition() {
            Schema v1 = order(V1_SYMBOLS, "CREATED");
            assertThat(analyse(v1, v1, List.of(consumer("svc", SWITCH_ON_CREATED)))).isEmpty();
        }

        @Test
        @DisplayName("an unrelated change to the same schema does not trigger the rule")
        void noFindingForUnrelatedChange() {
            Schema v1 = order(V1_SYMBOLS, "CREATED");
            Schema v2 = new Schema.Parser().parse("""
                    {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                      {"name":"orderId","type":"string"},
                      {"name":"channel","type":"string","default":"WEB"},
                      {"name":"status","type":{"type":"enum","name":"OrderStatus",
                         "symbols":[%s],"default":"CREATED"}}]}
                    """.formatted(V1_SYMBOLS));

            assertThat(analyse(v1, v2, List.of(consumer("svc", SWITCH_ON_CREATED)))).isEmpty();
        }

        @Test
        @DisplayName("removing a symbol is not this rule's concern")
        void noFindingForRemovedSymbol() {
            assertThat(analyse(order(V2_SYMBOLS, "CREATED"), order(V1_SYMBOLS, "CREATED"),
                    List.of(consumer("svc", SWITCH_ON_CREATED)))).isEmpty();
        }

        @Test
        @DisplayName("the enum is new in the target, so no older consumer was generated against it")
        void noFindingWhenEnumIsNew() {
            Schema v1 = new Schema.Parser().parse("""
                    {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
                      {"name":"orderId","type":"string"}]}
                    """);

            assertThat(analyse(v1, order(V2_SYMBOLS, "CREATED"),
                    List.of(consumer("svc", SWITCH_ON_CREATED)))).isEmpty();
        }

        @Test
        @DisplayName("a switch over a different enum that shares a constant name is ignored")
        void noFindingForUnrelatedEnumSwitch() {
            ConsumerDefinition other = new ConsumerDefinition("payments", null,
                    "com.example.orders.OrderEvent", ConsumerSourceType.BUILT_IN_SAMPLE,
                    List.of(new ConsumerSourceFile("payments/Handler.java", """
                            import com.example.payments.PaymentStatus;
                            public class Handler {
                                void handle(Payment payment) {
                                    switch (payment.getStatus()) {
                                        case CREATED -> start();
                                        case SETTLED -> finish();
                                    }
                                }
                            }
                            """)));

            assertThat(analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"),
                    List.of(other))).isEmpty();
        }

        @Test
        @DisplayName("no consumers registered for the schema")
        void noFindingWithoutConsumers() {
            assertThat(analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"), List.of()))
                    .isEmpty();
        }
    }

    // ---- robustness ------------------------------------------------------------------------

    @Nested
    @DisplayName("robustness")
    class Robustness {

        @Test
        @DisplayName("unparseable source is reported as a warning, not an exception")
        void unparseableSourceProducesWarning() {
            ConsumerDefinition broken = new ConsumerDefinition("broken-service", null,
                    "com.example.orders.OrderEvent", ConsumerSourceType.BUILT_IN_SAMPLE,
                    List.of(new ConsumerSourceFile("broken-service/Broken.java",
                            "public class Broken { this is not java (((")));

            List<String> warnings = new ArrayList<>();
            List<OperationalRiskFinding> findings = rule.apply(
                    diffEngine.diff(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED")),
                    order(V1_SYMBOLS, "CREATED"), List.of(broken), warnings);

            assertThat(findings).isEmpty();
            assertThat(warnings).singleElement().asString()
                    .contains("broken-service")
                    .contains("not valid Java source");
        }

        @Test
        @DisplayName("one broken file does not stop analysis of a valid one")
        void brokenFileDoesNotBlockOthers() {
            ConsumerDefinition mixed = new ConsumerDefinition("mixed-service", null,
                    "com.example.orders.OrderEvent", ConsumerSourceType.BUILT_IN_SAMPLE,
                    List.of(new ConsumerSourceFile("mixed/Broken.java", "not java at all ((("),
                            new ConsumerSourceFile("mixed/Handler.java", """
                                    import com.example.orders.OrderStatus;
                                    public class Handler {
                                        void handle(OrderEvent order) {
                                            if (order.getStatus() == OrderStatus.CREATED) { notifyNew(); }
                                        }
                                    }
                                    """)));

            List<String> warnings = new ArrayList<>();
            List<OperationalRiskFinding> findings = rule.apply(
                    diffEngine.diff(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED")),
                    order(V1_SYMBOLS, "CREATED"), List.of(mixed), warnings);

            assertThat(warnings).hasSize(1);
            assertThat(findings).singleElement().satisfies(finding ->
                    assertThat(finding.evidence().fileName()).isEqualTo("Handler.java"));
        }

        @Test
        @DisplayName("repeated runs return identical findings in identical order")
        void resultsAreDeterministic() {
            List<ConsumerDefinition> consumers = List.of(
                    consumer("zeta-service", SWITCH_ON_CREATED),
                    consumer("alpha-service", SWITCH_ON_CREATED),
                    consumer("mid-service", SWITCH_ON_CREATED));

            List<OperationalRiskFinding> first =
                    analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"), consumers);

            assertThat(first).extracting(OperationalRiskFinding::consumer)
                    .containsExactly("alpha-service", "mid-service", "zeta-service");
            for (int run = 0; run < 20; run++) {
                assertThat(analyse(order(V1_SYMBOLS, "CREATED"), order(V2_SYMBOLS, "CREATED"), consumers))
                        .isEqualTo(first);
            }
        }
    }

}
