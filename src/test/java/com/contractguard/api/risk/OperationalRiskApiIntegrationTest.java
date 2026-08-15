package com.contractguard.api.risk;

import com.contractguard.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationalRiskApiIntegrationTest extends AbstractIntegrationTest {

    private String projectId;
    private String v1Id;
    private String v2Id;

    @BeforeEach
    void seedProject() throws Exception {
        projectId = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"E-commerce Orders\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        v1Id = postSchema(sample("order-v1.avsc"));
        v2Id = postSchema(sample("order-v2.avsc"));
    }

    @Test
    @DisplayName("the sample change reports HIGH risk against the notification service")
    void reportsHighRiskWithEvidence() throws Exception {
        // The sample consumer gives CREATED meaning in two places, so each gets its own finding
        // and its own line to fix.
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.targetVersion.versionNumber").value(2))
                .andExpect(jsonPath("$.overallSeverity").value("HIGH"))
                .andExpect(jsonPath("$.findingCount").value(2))

                .andExpect(jsonPath("$.findings[0].ruleId").value("ENUM_SEMANTIC_FALLBACK_RISK"))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.findings[0].consumer").value("order-notification-service"))
                .andExpect(jsonPath("$.findings[0].schemaPath").value("OrderEvent.status"))
                .andExpect(jsonPath("$.findings[0].attributes.newSymbol").value("RETURNED"))
                .andExpect(jsonPath("$.findings[0].attributes.fallbackSymbol").value("CREATED"))
                .andExpect(jsonPath("$.findings[0].attributes.usageKind").value("SWITCH_CASE"))
                .andExpect(jsonPath("$.findings[0].evidence.sourceFile").value("OrderStatusHandler.java"))
                .andExpect(jsonPath("$.findings[0].evidence.line").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.findings[0].evidence.snippet")
                        .value("case CREATED -> sendNewOrderNotification(order);"))
                .andExpect(jsonPath("$.findings[0].reason").isNotEmpty())

                .andExpect(jsonPath("$.findings[1].attributes.usageKind").value("EQUALITY_COMPARISON"))
                .andExpect(jsonPath("$.findings[1].evidence.snippet")
                        .value("return order.getStatus() == OrderStatus.CREATED;"))

                .andExpect(jsonPath("$.analysedConsumers.length()").value(3))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    @DisplayName("findings are ordered by consumer, then file, then line")
    void findingsAreOrderedByLocation() throws Exception {
        assertThat(JsonPath.<java.util.List<Integer>>read(riskBody(), "$.findings[*].evidence.line"))
                .isSorted();
    }

    @Test
    @DisplayName("the safe consumers are analysed but not flagged")
    void safeConsumersAreNotFlagged() throws Exception {
        String body = riskBody();

        assertThat(JsonPath.<java.util.List<String>>read(body, "$.analysedConsumers"))
                .containsExactly("order-analytics-service", "order-notification-service",
                        "order-returns-service");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.findings[*].consumer"))
                .containsOnly("order-notification-service");
    }

    @Test
    @DisplayName("comparing a version with itself reports no risk")
    void noChangeMeansNoRisk() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}", projectId, v1Id, v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.findings").isEmpty());
    }

    @Test
    @DisplayName("removing the symbol again is not this rule's concern")
    void reverseDirectionReportsNoRisk() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}", projectId, v2Id, v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallSeverity").value("NONE"));
    }

    @Test
    @DisplayName("a schema with no registered consumers reports no risk")
    void unknownSchemaHasNoConsumers() throws Exception {
        String unrelatedV1 = postSchema("""
                {"type":"record","name":"PaymentEvent","namespace":"com.example.payments","fields":[
                  {"name":"paymentId","type":"string"},
                  {"name":"state","type":{"type":"enum","name":"PaymentState",
                     "symbols":["CREATED","SETTLED"],"default":"CREATED"}}]}
                """);
        String unrelatedV2 = postSchema("""
                {"type":"record","name":"PaymentEvent","namespace":"com.example.payments","fields":[
                  {"name":"paymentId","type":"string"},
                  {"name":"state","type":{"type":"enum","name":"PaymentState",
                     "symbols":["CREATED","SETTLED","DISPUTED"],"default":"CREATED"}}]}
                """);

        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}",
                        projectId, unrelatedV1, unrelatedV2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.analysedConsumers").isEmpty());
    }

    @Test
    @DisplayName("risk and compatibility stay separate: compatible in one direction, still HIGH risk")
    void compatibilityAndRiskAreIndependent() throws Exception {
        String compatibility = mockMvc.perform(
                        get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Avro raises nothing about the enum: the default absorbs the new symbol.
        assertThat(JsonPath.<String>read(compatibility, "$.results.backward.status")).isEqualTo("PASS");
        assertThat(JsonPath.<java.util.List<String>>read(compatibility, "$.results.forward.issues[*].path"))
                .doesNotContain("OrderEvent.status");

        // The risk endpoint independently reports HIGH for that very field.
        assertThat(JsonPath.<String>read(riskBody(), "$.overallSeverity")).isEqualTo("HIGH");
        assertThat(JsonPath.<java.util.List<String>>read(riskBody(), "$.findings[*].schemaPath"))
                .containsOnly("OrderEvent.status");
    }

    @Test
    @DisplayName("the risk payload carries no compatibility fields and vice versa")
    void payloadsDoNotOverlap() throws Exception {
        String risk = riskBody();
        String compatibility = mockMvc.perform(
                        get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v2Id))
                .andReturn().getResponse().getContentAsString();

        assertThat(risk).doesNotContain("backward").doesNotContain("issueType").doesNotContain("PASS");
        assertThat(compatibility).doesNotContain("ruleId").doesNotContain("overallSeverity");
    }

    @Test
    @DisplayName("repeated calls return an identical response")
    void responseIsDeterministic() throws Exception {
        String first = riskBody();
        for (int run = 0; run < 5; run++) {
            assertThat(riskBody()).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("an unknown version returns 404")
    void unknownVersionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}",
                        projectId, v1Id, "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a version from another project cannot be analysed")
    void crossProjectAnalysisReturns404() throws Exception {
        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Unrelated\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}", otherProject, v1Id, v2Id))
                .andExpect(status().isNotFound());
    }

    private String riskBody() throws Exception {
        return mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/risk/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String postSchema(String schemaContent) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": " + TextNode.valueOf(schemaContent) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String sample(String fileName) {
        try (var stream = OperationalRiskApiIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing", e);
        }
    }
}
