package com.contractguard.rollout;

import com.contractguard.history.AnalysisRun;
import com.contractguard.history.AnalysisRunService;
import com.contractguard.history.AnalysisStatus;
import com.contractguard.shared.ConflictException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RolloutPlanService {

    private final AnalysisRunService analysisRunService;
    private final RolloutPlanner planner;

    public RolloutPlanService(AnalysisRunService analysisRunService, RolloutPlanner planner) {
        this.analysisRunService = analysisRunService;
        this.planner = planner;
    }

    /** Derived from the stored snapshot on every request; nothing is recomputed or re-scanned. */
    public RolloutPlan planFor(UUID analysisId) {
        AnalysisRun run = analysisRunService.getById(analysisId);
        if (run.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ConflictException("Analysis " + analysisId + " is " + run.getStatus()
                    + "; rollout guidance requires a COMPLETED analysis");
        }
        return planner.plan(run);
    }
}
