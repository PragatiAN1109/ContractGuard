import { API_BASE, http } from './client';
import type { RegisteredConsumer } from './types';

export const consumersApi = {
  list: (projectId: string) =>
    http.get<RegisteredConsumer[]>(`${API_BASE}/projects/${projectId}/consumers`),

  get: (projectId: string, consumerId: string) =>
    http.get<RegisteredConsumer>(`${API_BASE}/projects/${projectId}/consumers/${consumerId}`),

  /** Registers a new immutable revision from uploaded .java files or a single .zip. */
  register: (
    projectId: string,
    input: { serviceName: string; consumesSchema: string; description?: string; files: File[] },
  ) => {
    const form = new FormData();
    form.append('serviceName', input.serviceName);
    form.append('consumesSchema', input.consumesSchema);
    if (input.description) form.append('description', input.description);
    input.files.forEach((file) => form.append('files', file));
    return http.postForm<RegisteredConsumer>(`${API_BASE}/projects/${projectId}/consumers`, form);
  },
};
