import { API_BASE, http } from './client';
import type { AnalysisRun, AnalysisRunSummary } from './types';

export const analysesApi = {
  /** The primary product action: runs and persists a full analysis. */
  create: (projectId: string, sourceSchemaVersionId: string, targetSchemaVersionId: string) =>
    http.post<AnalysisRun>(`${API_BASE}/projects/${projectId}/analyses`, {
      sourceSchemaVersionId,
      targetSchemaVersionId,
    }),

  history: (projectId: string) =>
    http.get<AnalysisRunSummary[]>(`${API_BASE}/projects/${projectId}/analyses`),

  /** Reads the persisted snapshot. Never re-runs the analysis. */
  get: (analysisId: string) => http.get<AnalysisRun>(`${API_BASE}/analyses/${analysisId}`),
};
