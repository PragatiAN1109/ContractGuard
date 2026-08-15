package com.contractguard.api.schema;

import com.contractguard.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchemaVersionApiIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDER_EVENT_V1 = """
            {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
              {"name":"orderId","type":"string"},
              {"name":"status","type":{"type":"enum","name":"OrderStatus",
                 "symbols":["CREATED","PAID","SHIPPED"]}}]}
            """;

    private static final String ORDER_EVENT_V2 = """
            {"type":"record","name":"OrderEvent","namespace":"com.example.orders","fields":[
              {"name":"orderId","type":"string"},
              {"name":"status","type":{"type":"enum","name":"OrderStatus",
                 "symbols":["CREATED","PAID","SHIPPED","RETURNED"]}}]}
            """;

    private String projectId;

    @BeforeEach
    void createProject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Orders\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        projectId = JsonPath.read(response, "$.id");
    }

    @Test
    @DisplayName("POST stores a valid schema as version 1 with a SHA-256 hash")
    void storesFirstSchema() throws Exception {
        postSchema(ORDER_EVENT_V1)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("version numbers increase per project")
    void assignsIncreasingVersionNumbers() throws Exception {
        postSchema(ORDER_EVENT_V1).andExpect(jsonPath("$.versionNumber").value(1));
        postSchema(ORDER_EVENT_V2).andExpect(jsonPath("$.versionNumber").value(2));
    }

    @Test
    @DisplayName("the same schema cannot be stored twice in one project")
    void rejectsDuplicateSchema() throws Exception {
        postSchema(ORDER_EVENT_V1).andExpect(status().isCreated());

        postSchema(ORDER_EVENT_V1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail")
                        .value("This schema is already stored in the project as version 1"));
    }

    @Test
    @DisplayName("reformatting a schema does not make it a new version")
    void reformattedSchemaIsStillADuplicate() throws Exception {
        postSchema(ORDER_EVENT_V1).andExpect(status().isCreated());

        String reformatted = ORDER_EVENT_V1.replace("\n", " ").replace("  ", " ");
        postSchema(reformatted).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the same schema may exist in a different project")
    void duplicateIsScopedToProject() throws Exception {
        postSchema(ORDER_EVENT_V1).andExpect(status().isCreated());

        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Other\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/projects/{projectId}/schemas", otherProject)
                        .contentType("application/json")
                        .content(requestBody(ORDER_EVENT_V1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    @DisplayName("a malformed schema is rejected with 400 and an explanation")
    void rejectsInvalidSchema() throws Exception {
        postSchema("{\"type\": \"record\", \"name\": \"Broken\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Avro schema"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Invalid Avro schema:")));
    }

    @Test
    @DisplayName("content that is not JSON at all is rejected with 400")
    void rejectsNonJsonSchema() throws Exception {
        postSchema("definitely not a schema")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Avro schema"));
    }

    @Test
    @DisplayName("an empty schema body fails bean validation")
    void rejectsEmptySchemaContent() throws Exception {
        postSchema("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.schemaContent").isNotEmpty());
    }

    @Test
    @DisplayName("GET lists schema versions in ascending order without the schema body")
    void listsSchemaVersions() throws Exception {
        postSchema(ORDER_EVENT_V1);
        postSchema(ORDER_EVENT_V2);

        mockMvc.perform(get("/api/v1/projects/{projectId}/schemas", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[1].versionNumber").value(2))
                .andExpect(jsonPath("$[0].schemaContent").doesNotExist());
    }

    @Test
    @DisplayName("GET by id returns the stored schema content")
    void getsSchemaVersionById() throws Exception {
        String schemaVersionId = JsonPath.read(
                postSchema(ORDER_EVENT_V1).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/projects/{projectId}/schemas/{id}", projectId, schemaVersionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schemaVersionId))
                .andExpect(jsonPath("$.schemaContent").value(ORDER_EVENT_V1));
    }

    @Test
    @DisplayName("posting to an unknown project returns 404")
    void unknownProjectReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/schemas", "11111111-1111-1111-1111-111111111111")
                        .contentType("application/json")
                        .content(requestBody(ORDER_EVENT_V1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a schema version from another project is not reachable")
    void schemaVersionIsScopedToItsProject() throws Exception {
        String schemaVersionId = JsonPath.read(
                postSchema(ORDER_EVENT_V1).andReturn().getResponse().getContentAsString(), "$.id");

        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Unrelated\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/projects/{projectId}/schemas/{id}", otherProject, schemaVersionId))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions postSchema(String schemaContent) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/schemas", projectId)
                .contentType("application/json")
                .content(requestBody(schemaContent)));
    }

    /** Wraps a raw schema as the JSON string value of schemaContent. */
    private static String requestBody(String schemaContent) {
        return "{\"schemaContent\": " + com.fasterxml.jackson.databind.node.TextNode.valueOf(schemaContent) + "}";
    }
}
