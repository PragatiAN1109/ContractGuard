import { API_BASE, http } from './client';
import type { SchemaVersion, SchemaVersionSummary } from './types';

export const schemasApi = {
  list: (projectId: string) =>
    http.get<SchemaVersionSummary[]>(`${API_BASE}/projects/${projectId}/schemas`),

  get: (projectId: string, schemaVersionId: string) =>
    http.get<SchemaVersion>(`${API_BASE}/projects/${projectId}/schemas/${schemaVersionId}`),

  create: (projectId: string, schemaContent: string) =>
    http.post<SchemaVersion>(`${API_BASE}/projects/${projectId}/schemas`, { schemaContent }),
};
