package com.contractguard.api.risk;

import com.contractguard.consumeranalysis.OperationalRiskAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/schemas")
public class OperationalRiskController {

    private final OperationalRiskAnalysisService riskAnalysisService;

    public OperationalRiskController(OperationalRiskAnalysisService riskAnalysisService) {
        this.riskAnalysisService = riskAnalysisService;
    }

    /** Consumer-aware operational risk for a proposed change. Sibling of /diff and /compatibility. */
    @GetMapping("/{sourceSchemaVersionId}/risk/{targetSchemaVersionId}")
    public OperationalRiskReportResponse risk(@PathVariable UUID projectId,
                                              @PathVariable UUID sourceSchemaVersionId,
                                              @PathVariable UUID targetSchemaVersionId) {
        return OperationalRiskReportResponse.from(
                riskAnalysisService.analyse(projectId, sourceSchemaVersionId, targetSchemaVersionId));
    }
}
