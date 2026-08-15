package com.contractguard.api.rollout;

import com.contractguard.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RolloutApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("the sample analysis yields consumer-first guidance naming the real consumer")
    void sampleProducesConsumerFirstGuidance() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        mockMvc.perform(get("/api/v1/analyses/{id}/rollout", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.strategy").value("CONSUMER_FIRST"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers
                        .containsString("order-notification-service")))

                // FORWARD fails on customerEmail, so old readers must be upgraded first.
                .andExpect(jsonPath("$.steps[0].order").value(1))
                .andExpect(jsonPath("$.steps[0].action").value("UPGRADE_CONSUMERS"))
                .andExpect(jsonPath("$.steps[0].reason").value(org.hamcrest.Matchers
                        .containsString("OrderEvent.customerEmail")))

                .andExpect(jsonPath("$.steps[1].action").value("UPDATE_CONSUMER"))
                .andExpect(jsonPath("$.steps[1].target").value("order-notification-service"))
                .andExpect(jsonPath("$.steps[1].reason").value(org.hamcrest.Matchers
                        .containsString("'RETURNED' as 'CREATED'")))
                .andExpect(jsonPath("$.steps[2].action").value("VERIFY_CONSUMER_DEPLOYMENT"))
                .andExpect(jsonPath("$.steps[3].action").value("DEPLOY_SCHEMA"))
                .andExpect(jsonPath("$.steps[4].action").value("BEGIN_PRODUCING"))
                .andExpect(jsonPath("$.steps.length()").value(5))

                .andExpect(jsonPath("$.limitations.length()").value(3));
    }

    @Test
    @DisplayName("two findings for one consumer produce a single update step")
    void duplicateFindingsCollapse() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        // The sample stores two findings for the same consumer.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_risk_finding WHERE analysis_run_id = ?::uuid",
                Integer.class, analysisId)).isEqualTo(2);

        String body = rolloutBody(analysisId);
        assertThat(JsonPath.<List<String>>read(body, "$.steps[?(@.action=='UPDATE_CONSUMER')].target"))
                .containsExactly("order-notification-service");
    }

    @Test
    @DisplayName("guidance never claims the change is safe")
    void guidanceNeverClaimsSafety() throws Exception {
        String body = rolloutBody(runAnalysis(v1Id, v2Id));

        assertThat(body).doesNotContain("SAFE").doesNotContain("UNSAFE");
        assertThat(JsonPath.<String>read(body, "$.strategy")).isNotEqualTo("SAFE");
        assertThat(JsonPath.<List<String>>read(body, "$.limitations"))
                .anySatisfy(limitation -> assertThat(limitation).contains("not proof that the change is safe"));
    }

    @Test
    @DisplayName("an analysis with no findings reports that no rule fired")
    void cleanAnalysisReportsNoConstraint() throws Exception {
        String analysisId = runAnalysis(v1Id, v1Id);

        mockMvc.perform(get("/api/v1/analyses/{id}/rollout", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("NO_CONSTRAINT_IDENTIFIED"))
                .andExpect(jsonPath("$.steps").isEmpty())
                .andExpect(jsonPath("$.summary").value(
                        "No rollout constraint was identified by the currently implemented ContractGuard rules."));
    }

    @Test
    @DisplayName("guidance is derived from the persisted snapshot, not from live consumer source")
    void guidanceUsesPersistedFindings() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        // Rewrite the stored finding. Re-running consumer analysis would restore the original
        // values; reading the snapshot must surface the edited ones.
        jdbcTemplate.update(
                "UPDATE analysis_risk_finding SET consumer = 'renamed-service' WHERE analysis_run_id = ?::uuid",
                analysisId);
        jdbcTemplate.update("""
                UPDATE analysis_finding_attribute SET attribute_value = 'ARCHIVED'
                WHERE attribute_key = 'newSymbol' AND finding_id IN
                  (SELECT id FROM analysis_risk_finding WHERE analysis_run_id = ?::uuid)""", analysisId);

        String body = rolloutBody(analysisId);
        assertThat(JsonPath.<List<String>>read(body, "$.steps[?(@.action=='UPDATE_CONSUMER')].target"))
                .containsExactly("renamed-service");
        assertThat(body).contains("'ARCHIVED'").doesNotContain("order-notification-service");
    }

    @Test
    @DisplayName("repeated requests return an identical plan")
    void planIsDeterministic() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        String first = rolloutBody(analysisId);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(rolloutBody(analysisId)).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("two analyses of the same project get independent plans")
    void plansAreIndependentPerAnalysis() throws Exception {
        String forward = runAnalysis(v1Id, v2Id);
        String reverse = runAnalysis(v2Id, v1Id);

        assertThat(JsonPath.<String>read(rolloutBody(forward), "$.strategy")).isEqualTo("CONSUMER_FIRST");
        // v2 to v1 removes a symbol: BACKWARD fails, so that direction is blocked.
        assertThat(JsonPath.<String>read(rolloutBody(reverse), "$.strategy"))
                .isEqualTo("BLOCKED_BY_COMPATIBILITY");
    }

    @Test
    @DisplayName("an unknown analysis returns 404")
    void unknownAnalysisReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/{id}/rollout", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("rollout does not alter the analysis snapshot")
    void rolloutIsReadOnly() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);
        String before = mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andReturn().getResponse().getContentAsString();

        rolloutBody(analysisId);

        assertThat(mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andReturn().getResponse().getContentAsString()).isEqualTo(before);
    }

    private String rolloutBody(String analysisId) throws Exception {
        return mockMvc.perform(get("/api/v1/analyses/{id}/rollout", analysisId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String runAnalysis(String sourceId, String targetId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/analyses", projectId)
                        .contentType("application/json")
                        .content("{\"sourceSchemaVersionId\":\"" + sourceId
                                + "\",\"targetSchemaVersionId\":\"" + targetId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.analysisId");
    }

    private String postSchema(String schemaContent) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": " + TextNode.valueOf(schemaContent) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String sample(String fileName) {
        try (var stream = RolloutApiIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing", e);
        }
    }
}
