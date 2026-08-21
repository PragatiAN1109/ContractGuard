# Sample: insurance claim assessment

`claim-v1.avsc` → `claim-v2.avsc` adds `REQUIRES_MANUAL_REVIEW` to `AssessmentOutcome`, whose v1
enum defaults to `REPAIRABLE`.

Avro reports no compatibility issue at `ClaimAssessment.outcome` in either direction, because the
default absorbs the new symbol. `repair-workflow-service` was generated against v1, so it receives
`REPAIRABLE` and **creates a repair plan and reserves parts** for a claim an assessor actually
flagged for manual review.

Evidence: `RepairWorkflowHandler.java` — `case REPAIRABLE -> createRepairPlan(assessment);`
