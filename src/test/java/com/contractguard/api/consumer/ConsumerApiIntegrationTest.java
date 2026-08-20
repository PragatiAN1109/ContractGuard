package com.contractguard.api.consumer;

import com.contractguard.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registration, relevance and provenance for uploaded consumer source. */
class ConsumerApiIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH_V1 = """
            {"type":"record","name":"AuthorizationEvent","namespace":"com.example.payments","fields":[
              {"name":"authorizationId","type":"string"},
              {"name":"outcome","type":{"type":"enum","name":"AuthorizationOutcome",
                 "symbols":["APPROVED","DECLINED","REFERRED"],"default":"APPROVED"}}]}
            """;

    private static final String AUTH_V2 = """
            {"type":"record","name":"AuthorizationEvent","namespace":"com.example.payments","fields":[
              {"name":"authorizationId","type":"string"},
              {"name":"outcome","type":{"type":"enum","name":"AuthorizationOutcome",
                 "symbols":["APPROVED","DECLINED","REFERRED","PARTIALLY_APPROVED"],"default":"APPROVED"}}]}
            """;

    /** Gives the fallback symbol APPROVED its own business behaviour: unsafe. */
    private static final String UNSAFE_CORRELATOR = """
            package com.example.payments.correlation;

            import com.example.payments.AuthorizationOutcome;

            public class TransactionCorrelator {
                public void onAuthorization(AuthorizationEvent event) {
                    switch (event.getOutcome()) {
                        case APPROVED -> correlateWithPostedTransaction();
                        case DECLINED -> recordDecline();
                        case REFERRED -> queueForReview();
                    }
                }
            }
            """;

    /** Already knows PARTIALLY_APPROVED, so nothing falls back for it: safe. */
    private static final String SAFE_CORRELATOR = """
            package com.example.payments.correlation;

            import com.example.payments.AuthorizationOutcome;

            public class TransactionCorrelator {
                public void onAuthorization(AuthorizationEvent event) {
                    switch (event.getOutcome()) {
                        case APPROVED -> correlateWithPostedTransaction();
                        case PARTIALLY_APPROVED -> correlatePartialAmount();
                        case DECLINED -> recordDecline();
                        case REFERRED -> queueForReview();
                    }
                }
            }
            """;

    private String projectId;
    private String v1Id;
    private String v2Id;

    @BeforeEach
    void seedProject() throws Exception {
        projectId = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Payments\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        v1Id = postSchema(AUTH_V1);
        v2Id = postSchema(AUTH_V2);
    }

    @Test
    @DisplayName("registering a consumer source stores a revision with its files")
    void registersConsumerSource() throws Exception {
        mockMvc.perform(register("transaction-correlator", UNSAFE_CORRELATOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("transaction-correlator"))
                .andExpect(jsonPath("$.consumesSchema").value("com.example.payments.AuthorizationEvent"))
                .andExpect(jsonPath("$.sourceType").value("UPLOADED_SOURCE"))
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.revision").isNotEmpty())
                .andExpect(jsonPath("$.sourceFiles[0]").value("TransactionCorrelator.java"))
                .andExpect(jsonPath("$.supersededAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("a malformed upload is rejected with a problem response")
    void rejectsMalformedUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/projects/{p}/consumers", projectId)
                        .file(new MockMultipartFile("files", "notes.txt", "text/plain",
                                "not java".getBytes(StandardCharsets.UTF_8)))
                        .param("serviceName", "bad-service")
                        .param("consumesSchema", "com.example.payments.AuthorizationEvent"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid consumer source upload"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Only .java")));
    }

    @Test
    @DisplayName("consumers are listed per project")
    void listsConsumersByProject() throws Exception {
        mockMvc.perform(register("transaction-correlator", UNSAFE_CORRELATOR)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/{p}/consumers", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("transaction-correlator"));

        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json").content("{\"name\": \"Unrelated\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/v1/projects/{p}/consumers", otherProject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("with no registered consumer, no source-backed finding is produced")
    void noConsumerMeansNoFindings() throws Exception {
        mockMvc.perform(analyse())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.operationalRisk.findings").isEmpty())
                .andExpect(jsonPath("$.consumerAnalysis.consumerCount").value(0));
    }

    @Test
    @DisplayName("uploaded source triggers the enum-fallback rule with its own provenance")
    void uploadedSourceTriggersTheRule() throws Exception {
        String revision = JsonPath.read(
                mockMvc.perform(register("transaction-correlator", UNSAFE_CORRELATOR))
                        .andReturn().getResponse().getContentAsString(), "$.revision");

        mockMvc.perform(analyse())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("HIGH"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].consumer")
                        .value("transaction-correlator"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].attributes.newSymbol")
                        .value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].attributes.fallbackSymbol")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.operationalRisk.findings[0].evidence.snippet")
                        .value("case APPROVED -> correlateWithPostedTransaction();"))
                .andExpect(jsonPath("$.consumerAnalysis.consumers[0].sourceType")
                        .value("UPLOADED_SOURCE"))
                .andExpect(jsonPath("$.consumerAnalysis.consumers[0].revision").value(revision));
    }

    @Test
    @DisplayName("a consumer that already handles the new symbol is not flagged")
    void safeConsumerIsNotFlagged() throws Exception {
        mockMvc.perform(register("transaction-correlator", SAFE_CORRELATOR)).andExpect(status().isCreated());

        mockMvc.perform(analyse())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("NONE"))
                .andExpect(jsonPath("$.consumerAnalysis.consumerCount").value(1));
    }

    @Test
    @DisplayName("an old analysis keeps its original provenance after the source is replaced")
    void oldAnalysisKeepsOriginalProvenance() throws Exception {
        String firstRevision = JsonPath.read(
                mockMvc.perform(register("transaction-correlator", UNSAFE_CORRELATOR))
                        .andReturn().getResponse().getContentAsString(), "$.revision");
        String firstAnalysis = JsonPath.read(
                mockMvc.perform(analyse()).andReturn().getResponse().getContentAsString(), "$.analysisId");

        // Replace the source with a safe revision; the previous revision is superseded, not edited.
        String secondRevision = JsonPath.read(
                mockMvc.perform(register("transaction-correlator", SAFE_CORRELATOR))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.revision");
        assertThat(secondRevision).isNotEqualTo(firstRevision);

        String secondAnalysis = JsonPath.read(
                mockMvc.perform(analyse()).andReturn().getResponse().getContentAsString(), "$.analysisId");

        // The old run still reports the old revision and its finding.
        mockMvc.perform(get("/api/v1/analyses/{id}", firstAnalysis))
                .andExpect(jsonPath("$.consumerAnalysis.consumers[0].revision").value(firstRevision))
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("HIGH"));

        // The new run reflects the new revision and finds nothing.
        mockMvc.perform(get("/api/v1/analyses/{id}", secondAnalysis))
                .andExpect(jsonPath("$.consumerAnalysis.consumers[0].revision").value(secondRevision))
                .andExpect(jsonPath("$.operationalRisk.overallSeverity").value("NONE"));

        // Only the newest revision is active.
        mockMvc.perform(get("/api/v1/projects/{p}/consumers", projectId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].revision").value(secondRevision));
    }

    @Test
    @DisplayName("repeated analysis of the same revision is deterministic")
    void repeatedAnalysisIsDeterministic() throws Exception {
        mockMvc.perform(register("transaction-correlator", UNSAFE_CORRELATOR)).andExpect(status().isCreated());

        String first = findingsOf(JsonPath.read(
                mockMvc.perform(analyse()).andReturn().getResponse().getContentAsString(), "$.analysisId"));
        for (int run = 0; run < 3; run++) {
            assertThat(findingsOf(JsonPath.read(
                    mockMvc.perform(analyse()).andReturn().getResponse().getContentAsString(), "$.analysisId")))
                    .isEqualTo(first);
        }
    }

    @Test
    @DisplayName("a consumer for an unrelated schema is not analysed")
    void irrelevantConsumerIsNotAnalysed() throws Exception {
        mockMvc.perform(multipart("/api/v1/projects/{p}/consumers", projectId)
                        .file(javaPart("Unrelated.java", "public class Unrelated {}"))
                        .param("serviceName", "unrelated-service")
                        .param("consumesSchema", "com.example.other.SomethingElse"))
                .andExpect(status().isCreated());

        mockMvc.perform(analyse())
                .andExpect(jsonPath("$.consumerAnalysis.consumerCount").value(0))
                .andExpect(jsonPath("$.operationalRisk.findings").isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------------------

    private String findingsOf(String analysisId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/analyses/{id}", analysisId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.operationalRisk.findings").toString();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder analyse() {
        return post("/api/v1/projects/{p}/analyses", projectId)
                .contentType("application/json")
                .content("{\"sourceSchemaVersionId\":\"" + v1Id + "\",\"targetSchemaVersionId\":\"" + v2Id + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder register(
            String serviceName, String source) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart("/api/v1/projects/{p}/consumers", projectId)
                        .file(javaPart("TransactionCorrelator.java", source))
                        .param("serviceName", serviceName)
                        .param("consumesSchema", "com.example.payments.AuthorizationEvent");
    }

    private static MockMultipartFile javaPart(String name, String content) {
        return new MockMultipartFile("files", name, "text/x-java-source",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String postSchema(String schemaContent) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": "
                                + com.fasterxml.jackson.databind.node.TextNode.valueOf(schemaContent) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }
}
