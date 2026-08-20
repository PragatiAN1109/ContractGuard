import { API_BASE, http } from './client';
import type { RolloutPlan } from './types';

export const rolloutApi = {
  /** 409 when the analysis is not COMPLETED. */
  get: (analysisId: string) => http.get<RolloutPlan>(`${API_BASE}/analyses/${analysisId}/rollout`),
};
