package com.contractguard.api.analysis;

import com.contractguard.AbstractIntegrationTest;
import com.contractguard.consumeranalysis.OperationalRiskAnalysisService;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consumer analysis is forced to throw, to prove the FAILED record survives. */
class AnalysisFailureIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private OperationalRiskAnalysisService riskAnalysisService;

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

        when(riskAnalysisService.analyse(any(), any(), any()))
                .thenThrow(new IllegalStateException("consumer analysis exploded"));
    }

    @Test
    @DisplayName("a failure after compatibility leaves a FAILED run that is still fetchable")
    void failedRunSurvivesAndIsFetchable() throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/{p}/analyses", projectId)
                        .contentType("application/json")
                        .content("{\"sourceSchemaVersionId\":\"" + v1Id + "\",\"targetSchemaVersionId\":\"" + v2Id + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Analysis failed"))
                .andExpect(jsonPath("$.analysisId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String analysisId = JsonPath.read(body, "$.analysisId");

        // The run was not rolled back with the failing work.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, failure_code, failure_message, started_at, completed_at "
                        + "FROM analysis_run WHERE id = ?::uuid", analysisId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo("UNEXPECTED_ERROR");
        assertThat(row.get("started_at")).isNotNull();
        assertThat(row.get("completed_at")).isNotNull();

        // The raw exception message is never persisted or exposed.
        assertThat((String) row.get("failure_message")).doesNotContain("exploded");
        assertThat(body).doesNotContain("exploded");

        mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.operationalRisk.findings").isEmpty());
    }

    @Test
    @DisplayName("a failed run stores no partial results")
    void failedRunHasNoPartialResults() throws Exception {
        String analysisId = JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/analyses", projectId)
                        .contentType("application/json")
                        .content("{\"sourceSchemaVersionId\":\"" + v1Id + "\",\"targetSchemaVersionId\":\"" + v2Id + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.analysisId");

        // Compatibility succeeded in memory but nothing is written until the single commit at the end.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_compatibility_result WHERE analysis_run_id = ?::uuid",
                Integer.class, analysisId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM analysis_risk_finding WHERE analysis_run_id = ?::uuid",
                Integer.class, analysisId)).isZero();
    }

    @Test
    @DisplayName("a failed run still appears in project history")
    void failedRunAppearsInHistory() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{p}/analyses", projectId)
                .contentType("application/json")
                .content("{\"sourceSchemaVersionId\":\"" + v1Id + "\",\"targetSchemaVersionId\":\"" + v2Id + "\"}"));

        mockMvc.perform(get("/api/v1/projects/{p}/analyses", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].findingCount").value(0));
    }

    private String postSchema(String schemaContent) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": " + TextNode.valueOf(schemaContent) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String sample(String fileName) {
        try (var stream = AnalysisFailureIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing", e);
        }
    }
}
