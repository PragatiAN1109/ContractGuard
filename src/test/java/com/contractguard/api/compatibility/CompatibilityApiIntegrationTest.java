package com.contractguard.api.compatibility;

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

class CompatibilityApiIntegrationTest extends AbstractIntegrationTest {

    /** v1 plus one optional field with a default: compatible in every direction. */
    private static final String ORDER_V1_PLUS_OPTIONAL = """
            {"type":"record","name":"OrderEvent","namespace":"com.example.orders",
             "doc":"Emitted on every transition in the order lifecycle.",
             "fields":[
               {"name":"orderId","type":"string"},
               {"name":"customerEmail","type":"string"},
               {"name":"status","type":{"type":"enum","name":"OrderStatus",
                  "symbols":["CREATED","PAID","SHIPPED","DELIVERED","CANCELLED"],"default":"CREATED"}},
               {"name":"totalCents","type":"long"},
               {"name":"currency","type":"string","default":"USD"},
               {"name":"couponCode","type":["null","string"],"default":null},
               {"name":"giftMessage","type":["null","string"],"default":null},
               {"name":"items","type":{"type":"array","items":{"type":"record","name":"OrderLine",
                  "fields":[{"name":"sku","type":"string"},{"name":"quantity","type":"int"}]}}}]}
            """;

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
    @DisplayName("the sample change is backward compatible but not forward or fully compatible")
    void sampleReportsBackwardPassForwardFail() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.targetVersion.versionNumber").value(2))

                .andExpect(jsonPath("$.results.backward.mode").value("BACKWARD"))
                .andExpect(jsonPath("$.results.backward.status").value("PASS"))
                .andExpect(jsonPath("$.results.backward.issues").isEmpty())

                .andExpect(jsonPath("$.results.forward.status").value("FAIL"))
                .andExpect(jsonPath("$.results.forward.issues.length()").value(1))
                .andExpect(jsonPath("$.results.forward.issues[0].issueType").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.results.forward.issues[0].path").value("OrderEvent.customerEmail"))
                .andExpect(jsonPath("$.results.forward.issues[0].reason").isNotEmpty())

                .andExpect(jsonPath("$.results.full.status").value("FAIL"))
                .andExpect(jsonPath("$.results.full.summary")
                        .value("FULL requires both directions; FORWARD failed."))
                .andExpect(jsonPath("$.results.full.issues").isEmpty());
    }

    @Test
    @DisplayName("adding one optional field with a default passes every mode")
    void optionalFieldAdditionPassesEverything() throws Exception {
        String v3Id = postSchema(ORDER_V1_PLUS_OPTIONAL);

        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v3Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.backward.status").value("PASS"))
                .andExpect(jsonPath("$.results.forward.status").value("PASS"))
                .andExpect(jsonPath("$.results.full.status").value("PASS"))
                .andExpect(jsonPath("$.results.full.summary").value("Both directions are compatible."));
    }

    @Test
    @DisplayName("comparing a version with itself is fully compatible")
    void identicalVersionsAreFullyCompatible() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.full.status").value("PASS"));
    }

    @Test
    @DisplayName("reversing source and target reverses which direction fails")
    void reversingTheDirectionReversesTheVerdict() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v2Id, v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.backward.status").value("FAIL"))
                .andExpect(jsonPath("$.results.forward.status").value("PASS"))
                .andExpect(jsonPath("$.results.full.summary")
                        .value("FULL requires both directions; BACKWARD failed."));
    }

    @Test
    @DisplayName("an issue with no Avro location still serializes its path key")
    void issuePathIsAlwaysPresent() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v2Id))
                .andExpect(jsonPath("$.results.forward.issues[0]")
                        .value(org.hamcrest.Matchers.hasKey("path")));
    }

    @Test
    @DisplayName("repeated calls return an identical response")
    void responseIsDeterministic() throws Exception {
        String first = compatibilityBody();
        for (int run = 0; run < 5; run++) {
            assertThat(compatibilityBody()).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("compatibility and diff are separate endpoints with separate payloads")
    void compatibilityIsSeparateFromDiff() throws Exception {
        String compatibility = compatibilityBody();
        String diff = mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/diff/{t}", projectId, v1Id, v2Id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(compatibility).doesNotContain("changeType").doesNotContain("changeCount");
        assertThat(diff).doesNotContain("backward").doesNotContain("issueType");
    }

    @Test
    @DisplayName("an unknown target version returns 404")
    void unknownVersionReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}",
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

        mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", otherProject, v1Id, v2Id))
                .andExpect(status().isNotFound());
    }

    private String compatibilityBody() throws Exception {
        return mockMvc.perform(get("/api/v1/projects/{p}/schemas/{s}/compatibility/{t}", projectId, v1Id, v2Id))
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
        try (var stream = CompatibilityApiIntegrationTest.class
                .getResourceAsStream("/samples/ecommerce-order/" + fileName)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sample " + fileName + " is missing from the classpath", e);
        }
    }
}
