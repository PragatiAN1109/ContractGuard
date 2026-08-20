// Mirrors the backend DTOs in com.contractguard.api. Field names match exactly; no backend
// semantics are re-implemented here.

export interface Project {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

/** List projection — deliberately has no schemaContent. */
export interface SchemaVersionSummary {
  id: string;
  projectId: string;
  versionNumber: number;
  contentHash: string;
  createdAt: string;
}

export interface SchemaVersion extends SchemaVersionSummary {
  schemaContent: string;
}

export type CompatibilityStatus = 'PASS' | 'FAIL';
export type RiskSeverity = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
export type AnalysisStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface CompatibilityIssue {
  issueType: string;
  path: string | null;
  reason: string;
}

export interface CompatibilityModeResult {
  mode: string;
  status: CompatibilityStatus;
  summary: string;
  issues: CompatibilityIssue[];
}

export interface CompatibilityResults {
  backward: CompatibilityModeResult | null;
  forward: CompatibilityModeResult | null;
  full: CompatibilityModeResult | null;
}

export interface SourceEvidence {
  sourceFile: string;
  filePath: string;
  line: number;
  snippet: string;
}

export interface RiskFinding {
  ruleId: string;
  severity: RiskSeverity;
  consumer: string;
  schemaPath: string;
  attributes: Record<string, string>;
  evidence: SourceEvidence | null;
  reason: string;
}

export interface OperationalRisk {
  overallSeverity: RiskSeverity;
  findingCount: number;
  findings: RiskFinding[];
}

export interface AnalysisRun {
  analysisId: string;
  status: AnalysisStatus;
  projectId: string;
  sourceSchemaVersionId: string;
  targetSchemaVersionId: string;
  sourceVersion: number;
  targetVersion: number;
  compatibility: CompatibilityResults;
  operationalRisk: OperationalRisk;
  failureCode: string | null;
  failureMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

/** History listing: compatibility is three plain strings, not full mode objects. */
export interface AnalysisRunSummary {
  analysisId: string;
  status: AnalysisStatus;
  sourceVersion: number;
  targetVersion: number;
  compatibility: {
    backward: CompatibilityStatus | null;
    forward: CompatibilityStatus | null;
    full: CompatibilityStatus | null;
  };
  highestSeverity: RiskSeverity;
  findingCount: number;
  createdAt: string;
  completedAt: string | null;
}

export interface RolloutStep {
  order: number;
  action: string;
  target: string;
  reason: string | null;
}

export interface RolloutPlan {
  analysisId: string;
  strategy: 'CONSUMER_FIRST' | 'BLOCKED_BY_COMPATIBILITY' | 'NO_CONSTRAINT_IDENTIFIED';
  summary: string;
  steps: RolloutStep[];
  limitations: string[];
}

/** RFC 9457 problem details, as produced by ApiExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
  analysisId?: string;
}
