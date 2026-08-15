package com.contractguard;

import com.contractguard.api.health.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast check that the web layer is wired, with no database and no Docker.
 * Full-context startup is covered by the integration tests.
 */
@WebMvcTest(HealthController.class)
class ApplicationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the health endpoint reports UP")
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("ContractGuard"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }
}
