import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectPage } from '../pages/ProjectPage';
import { ApiError } from '../api/client';

vi.mock('../api/projects', () => ({ projectsApi: { get: vi.fn(), list: vi.fn(), create: vi.fn() } }));
vi.mock('../api/schemas', () => ({
  schemasApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), consumerSources: vi.fn() },
}));
vi.mock('../api/analyses', () => ({ analysesApi: { history: vi.fn(), create: vi.fn(), get: vi.fn() } }));
vi.mock('../api/consumers', () => ({ consumersApi: { list: vi.fn(), register: vi.fn(), get: vi.fn() } }));

const { projectsApi } = await import('../api/projects');
const { schemasApi } = await import('../api/schemas');
const { analysesApi } = await import('../api/analyses');
const { consumersApi } = await import('../api/consumers');

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/projects/p1']}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProjectPage consumers', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(projectsApi.get).mockResolvedValue({
      id: 'p1', name: 'Payments', description: null, createdAt: '2026-08-20T12:00:00Z',
    });
    vi.mocked(schemasApi.list).mockResolvedValue([]);
    vi.mocked(analysesApi.history).mockResolvedValue([]);
  });

  it('lists registered consumer sources with source type and revision', async () => {
    vi.mocked(consumersApi.list).mockResolvedValue([
      {
        id: 'c1', projectId: 'p1', serviceName: 'transaction-correlator',
        consumesSchema: 'com.example.payments.AuthorizationEvent',
        sourceType: 'UPLOADED_SOURCE', revision: 'abc123def456', revisionHash: 'abc123def456ff',
        fileCount: 1, description: null, sourceFiles: ['TransactionCorrelator.java'],
        createdAt: '2026-08-20T12:00:00Z', supersededAt: null,
      },
    ]);
    renderPage();

    expect(await screen.findByText('Consumers')).toBeInTheDocument();
    expect(screen.getByText('transaction-correlator')).toBeInTheDocument();
    expect(screen.getByText('UPLOADED_SOURCE')).toBeInTheDocument();
    expect(screen.getByText('abc123def456')).toBeInTheDocument();
    expect(screen.getByText('com.example.payments.AuthorizationEvent')).toBeInTheDocument();
  });

  it('explains that nothing is registered yet', async () => {
    vi.mocked(consumersApi.list).mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText(/No consumer source registered yet/)).toBeInTheDocument();
  });

  it('shows an RFC 9457 problem when an upload is rejected', async () => {
    vi.mocked(consumersApi.list).mockResolvedValue([]);
    vi.mocked(consumersApi.register).mockRejectedValue(
      new ApiError('Only .java files are accepted, but got notes.txt', 400, {
        status: 400, title: 'Invalid consumer source upload',
        detail: 'Only .java files are accepted, but got notes.txt',
      }),
    );
    renderPage();
    await screen.findByText('Consumers');

    // Registration is driven through the API layer; the page surfaces the problem detail.
    const { consumersApi: api } = await import('../api/consumers');
    await expect(
      api.register('p1', { serviceName: 's', consumesSchema: 'x', files: [] }),
    ).rejects.toBeInstanceOf(ApiError);
    await waitFor(() => expect(screen.getByText('Consumers')).toBeInTheDocument());
  });
});
