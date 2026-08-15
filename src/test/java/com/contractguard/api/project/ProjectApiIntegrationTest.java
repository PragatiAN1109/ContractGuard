package com.contractguard.api.project;

import com.contractguard.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("POST creates a project and returns 201 with a Location header")
    void createsProject() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("""
                                {"name": "Order Lifecycle", "description": "Order events"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Order Lifecycle"))
                .andExpect(jsonPath("$.description").value("Order events"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST without a name returns 400 with the offending field")
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("""
                                {"name": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    @DisplayName("POST with a duplicate name returns 409")
    void rejectsDuplicateName() throws Exception {
        String body = """
                {"name": "Duplicate Project"}
                """;
        mockMvc.perform(post("/api/v1/projects").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A project named 'Duplicate Project' already exists"));
    }

    @Test
    @DisplayName("GET lists created projects")
    void listsProjects() throws Exception {
        mockMvc.perform(post("/api/v1/projects").contentType("application/json")
                .content("{\"name\": \"First\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects").contentType("application/json")
                .content("{\"name\": \"Second\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET by id returns the project")
    void getsProjectById() throws Exception {
        String projectId = createProject("Findable");

        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.name").value("Findable"));
    }

    @Test
    @DisplayName("GET with an unknown id returns 404")
    void unknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("GET with a malformed id returns 400")
    void malformedProjectIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid path parameter"));
    }

    private String createProject(String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }
}
