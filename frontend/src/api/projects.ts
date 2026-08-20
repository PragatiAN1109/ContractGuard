import { API_BASE, http } from './client';
import type { Project } from './types';

export const projectsApi = {
  list: () => http.get<Project[]>(`${API_BASE}/projects`),

  get: (projectId: string) => http.get<Project>(`${API_BASE}/projects/${projectId}`),

  create: (name: string, description?: string) =>
    http.post<Project>(`${API_BASE}/projects`, { name, description: description || null }),
};
