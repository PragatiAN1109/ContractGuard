package com.contractguard.api.analysis;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisApiIntegrationTest extends AbstractIntegrationTest {

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
    @DisplayName("running an analysis persists a COMPLETED run with both result sections")
    void createsCompletedAnalysis() throws Exception {
        mockMvc.perform(createAnalysis(v1Id, v2Id))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.analysisId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.targetVersion").value(2))

                .andExpect(jsonPath("$.compatibility.backward.status").value("PASS"))
                .andExpect(jsonPath("$.compatibility.backward.issues").isEmpty())
                .andExpect(jsonPath("$.compatibility.forward.status").value("FAIL"))
                .andExpect(jsonPath("$.compatibility.forward.issues[0].path")
                        .value("OrderEvent.customerEmail"))
                .andExpect(jsonPath("$.compatibility.full.status").value("FAIL"))

                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("HIGH"))
                .andExpect(jsonPath("$.operationalRisk.findingCount").value(2))
                .andExpect(jsonPath("$.operationalRisk.findings[0].ruleId")
                        .value("ENUM_SEMANTIC_FALLBACK_RISK"))

                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                // Present but null on a successful run, rather than omitted.
                .andExpect(jsonPath("$.failureCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.failureMessage").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("the run moves through RUNNING: startedAt precedes completedAt")
    void lifecycleTimestampsAreOrdered() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, created_at, started_at, completed_at FROM analysis_run WHERE id = ?::uuid",
                analysisId);

        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat((java.sql.Timestamp) row.get("started_at"))
                .isAfterOrEqualTo((java.sql.Timestamp) row.get("created_at"));
        assertThat((java.sql.Timestamp) row.get("completed_at"))
                .isAfterOrEqualTo((java.sql.Timestamp) row.get("started_at"));
    }

    @Test
    @DisplayName("source evidence survives persistence and reload")
    void evidenceSurvivesReload() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.sourceFile")
                        .value("OrderStatusHandler.java"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.line").value(20))
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.snippet")
                        .value("case CREATED -> sendNewOrderNotification(order);"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.filePath")
                        .value("order-notification-service/OrderStatusHandler.java"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].attributes.newSymbol").value("RETURNED"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].attributes.fallbackSymbol").value("CREATED"));
    }

    @Test
    @DisplayName("results are stored relationally, not as one JSON blob")
    void resultsAreStoredRelationally() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        assertThat(count("analysis_compatibility_result", "analysis_run_id", analysisId)).isEqualTo(3);
        assertThat(count("analysis_risk_finding", "analysis_run_id", analysisId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analysis_source_evidence e
                JOIN analysis_risk_finding f ON f.id = e.finding_id
                WHERE f.analysis_run_id = ?::uuid""", Integer.class, analysisId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analysis_finding_attribute a
                JOIN analysis_risk_finding f ON f.id = a.finding_id
                WHERE f.analysis_run_id = ?::uuid""", Integer.class, analysisId)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analysis_compatibility_issue i
                JOIN analysis_compatibility_result r ON r.id = i.compatibility_result_id
                WHERE r.analysis_run_id = ?::uuid""", Integer.class, analysisId)).isEqualTo(1);
    }

    @Test
    @DisplayName("fetching returns the stored snapshot, not a recomputation")
    void fetchReturnsStoredSnapshot() throws Exception {
        String analysisId = runAnalysis(v1Id, v2Id);

        // Corrupt the stored evidence directly. A recomputing endpoint would overwrite this;
        // a snapshot endpoint must return exactly what is in the table.
        jdbcTemplate.update("""
                UPDATE analysis_source_evidence SET snippet = 'STORED SNAPSHOT MARKER'
                WHERE finding_id IN (SELECT id FROM analysis_risk_finding WHERE analysis_run_id = ?::uuid)""",
                analysisId);

        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.snippet")
                        .value("STORED SNAPSHOT MARKER"));
    }

    @Test
    @DisplayName("history lists runs newest first without evidence")
    void historyIsLightweightAndNewestFirst() throws Exception {
        String first = runAnalysis(v1Id, v2Id);
        String second = runAnalysis(v2Id, v1Id);

        String body = mockMvc.perform(get("/api/v1/projects/{p}/analyses", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].analysisId").value(second))
                .andExpect(jsonPath("$[1].analysisId").value(first))
                .andExpect(jsonPath("$[1].compatibility.backward").value("PASS"))
                .andExpect(jsonPath("$[1].compatibility.forward").value("FAIL"))
                .andExpect(jsonPath("$[1].highestSeverity").value("HIGH"))
                .andExpect(jsonPath("$[1].findingCount").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("snippet").doesNotContain("evidence").doesNotContain("reason");
    }

    @Test
    @DisplayName("multiple analyses for one project stay independent")
    void analysesAreIndependent() throws Exception {
        String forward = runAnalysis(v1Id, v2Id);
        String reverse = runAnalysis(v2Id, v1Id);

        assertThat(forward).isNotEqualTo(reverse);
        mockMvc.perform(get("/api/v1/analyses/{id}", forward))
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("HIGH"))
                .andExpect(jsonPath("$.sourceVersion").value(1));
        mockMvc.perform(get("/api/v1/analyses/{id}", reverse))
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.sourceVersion").value(2));
    }

    @Test
    @DisplayName("analysing a version against itself is a valid empty result")
    void identicalVersionsProduceAnEmptyAnalysis() throws Exception {
        mockMvc.perform(createAnalysis(v1Id, v1Id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.compatibility.full.status").value("PASS"))
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.operationalRisk.findings").isEmpty());
    }

    @Test
    @DisplayName("compatibility and risk stay separate in the persisted snapshot")
    void sectionsRemainIndependent() throws Exception {
        String body = mockMvc.perform(createAnalysis(v1Id, v2Id))
                .andReturn().getResponse().getContentAsString();

        // Backward compatibility passes while risk is HIGH: no combined verdict exists.
        assertThat(JsonPath.<String>read(body, "$.compatibility.backward.status")).isEqualTo("PASS");
        assertThat(JsonPath.<String>read(body, "$.operationalRisk.overallSeverity")).isEqualTo("HIGH");
        assertThat(body).doesNotContain("\"safe\"").doesNotContain("verdict").doesNotContain("overallStatus");

        // The enum change that drives the risk raises no compatibility issue at all.
        List<String> issuePaths = JsonPath.read(body, "$.compatibility..issues[*].path");
        assertThat(issuePaths).doesNotContain("OrderEvent.status");
        assertThat(JsonPath.<List<String>>read(body, "$.operationalRisk.findings[*].schemaPath"))
                .containsOnly("OrderEvent.status");
    }

    @Test
    @DisplayName("a schema version from another project is rejected and creates no run")
    void crossProjectReferenceIsRejected() throws Exception {
        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Unrelated\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/projects/{p}/analyses", otherProject)
                        .contentType("application/json")
                        .content(analysisBody(v1Id, v2Id)))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM analysis_run", Integer.class)).isZero();
    }

    @Test
    @DisplayName("an unknown schema version returns 404 and creates no run")
    void unknownSchemaVersionIsRejected() throws Exception {
        mockMvc.perform(createAnalysis(v1Id, "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM analysis_run", Integer.class)).isZero();
    }

    @Test
    @DisplayName("an unknown project returns 404")
    void unknownProjectIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{p}/analyses", "11111111-1111-1111-1111-111111111111")
                        .contentType("application/json")
                        .content(analysisBody(v1Id, v2Id)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown analysis id returns 404")
    void unknownAnalysisReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("a missing schema version id fails validation")
    void missingFieldFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{p}/analyses", projectId)
                        .contentType("application/json")
                        .content("{\"sourceSchemaVersionId\": \"" + v1Id + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.targetSchemaVersionId").isNotEmpty());
    }

    @Test
    @DisplayName("history for a project with no analyses is empty")
    void emptyHistory() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/analyses", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private int count(String table, String column, String analysisId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid",
                Integer.class, analysisId);
    }

    private String runAnalysis(String sourceId, String targetId) throws Exception {
        return JsonPath.read(mockMvc.perform(createAnalysis(sourceId, targetId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.analysisId");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            createAnalysis(String sourceId, String targetId) {
        return post("/api/v1/projects/{p}/analyses", projectId)
                .contentType("application/json")
                .content(analysisBody(sourceId, targetId));
    }

    private static String analysisBody(String sourceId, String targetId) {
        return "{\"sourceSchemaVersionId\":\"" + sourceId + "\",\"targetSchemaVersionId\":\"" + targetId + "\"}";
    }

    private String postSchema(String schemaContent) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": " + TextNode.valueOf(schemaContent) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String sample(String fileName) {
        try (var stream = AnalysisApiIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing", e);
        }
    }
}
