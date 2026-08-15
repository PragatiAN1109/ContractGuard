package com.contractguard.api.schema;

import com.contractguard.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchemaComparisonApiIntegrationTest extends AbstractIntegrationTest {

    private String projectId;
    private String v1Id;
    private String v2Id;

    @BeforeEach
    void seedSampleProject() throws Exception {
        projectId = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"E-commerce Orders\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        v1Id = postSchema(sample("order-v1.avsc"));
        v2Id = postSchema(sample("order-v2.avsc"));
    }

    @Test
    @DisplayName("comparing the sample v1 to v2 returns the documented changes")
    void comparesSampleVersions() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceVersion.id").value(v1Id))
                .andExpect(jsonPath("$.sourceVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.targetVersion.id").value(v2Id))
                .andExpect(jsonPath("$.targetVersion.versionNumber").value(2))
                .andExpect(jsonPath("$.changeCount").value(6))
                .andExpect(jsonPath("$.changes[0].path").value("OrderEvent.channel"))
                .andExpect(jsonPath("$.changes[0].changeType").value("FIELD_ADDED"))
                .andExpect(jsonPath("$.changes[0].newValue").value("string"))
                // Present but null, rather than omitted: the field did not exist in the source.
                .andExpect(jsonPath("$.changes[0]").value(org.hamcrest.Matchers.hasKey("oldValue")))
                .andExpect(jsonPath("$.changes[0].oldValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.changes[1].path").value("OrderEvent.currency"))
                .andExpect(jsonPath("$.changes[1].changeType").value("DEFAULT_VALUE_CHANGED"))
                .andExpect(jsonPath("$.changes[1].oldValue").value("USD"))
                .andExpect(jsonPath("$.changes[1].newValue").value("UNSPECIFIED"))
                .andExpect(jsonPath("$.changes[5].path").value("OrderEvent.status"))
                .andExpect(jsonPath("$.changes[5].changeType").value("ENUM_SYMBOL_ADDED"))
                .andExpect(jsonPath("$.changes[5].newValue").value("RETURNED"));
    }

    @Test
    @DisplayName("comparing a version with itself reports no changes")
    void comparingAVersionWithItselfIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}", projectId, v1Id, v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeCount").value(0))
                .andExpect(jsonPath("$.changes").isEmpty());
    }

    @Test
    @DisplayName("repeated calls return an identical response")
    void responseIsDeterministic() throws Exception {
        String first = diffBody();
        for (int run = 0; run < 5; run++) {
            org.assertj.core.api.Assertions.assertThat(diffBody()).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("an unknown source version returns 404")
    void unknownSourceVersionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}",
                        projectId, "11111111-1111-1111-1111-111111111111", v2Id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a version belonging to another project cannot be compared")
    void crossProjectComparisonReturns404() throws Exception {
        String otherProject = JsonPath.read(mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"Unrelated\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}", otherProject, v1Id, v2Id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a primitive-root schema is rejected on submission")
    void primitiveRootSchemaIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{p}/schemas", projectId)
                        .contentType("application/json")
                        .content("{\"schemaContent\": \"\\\"string\\\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Avro schema"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid Avro schema: the root type must be a record, but was string"));
    }

    private String diffBody() throws Exception {
        return mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}", projectId, v1Id, v2Id))
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
        try (var stream = SchemaComparisonApiIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing from the classpath", e);
        }
    }
}
