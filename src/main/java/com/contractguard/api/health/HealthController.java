package com.contractguard.api.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health endpoint on the public API surface.
 *
 * Complements rather than replaces Actuator's /actuator/health, which stays the operational
 * probe; this one confirms the /api/v1 surface itself is reachable.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /** Maven project version, substituted into application.yml by resource filtering. */
    private final String version;

    HealthController(@Value("${contractguard.version}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "ContractGuard", version, "PHASE_1_SKELETON");
    }

    public record HealthResponse(String status, String application, String version, String phase) {}
}
