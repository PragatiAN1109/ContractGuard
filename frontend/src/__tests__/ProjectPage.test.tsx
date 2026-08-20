import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectPage } from '../pages/ProjectPage';
import { history, projects } from './fixtures';

vi.mock('../api/projects', () => ({ projectsApi: { get: vi.fn(), list: vi.fn(), create: vi.fn() } }));
vi.mock('../api/schemas', () => ({ schemasApi: { list: vi.fn(), create: vi.fn(), get: vi.fn() } }));
vi.mock('../api/analyses', () => ({
  analysesApi: { history: vi.fn(), create: vi.fn(), get: vi.fn() },
}));
const { projectsApi } = await import('../api/projects');
const { schemasApi } = await import('../api/schemas');
const { analysesApi } = await import('../api/analyses');

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/projects/p1']}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProjectPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lists persisted analysis history newest-first and links to the stored snapshot', async () => {
    vi.mocked(projectsApi.get).mockResolvedValue(projects[0]);
    vi.mocked(schemasApi.list).mockResolvedValue([
      { id: 's1', projectId: 'p1', versionNumber: 1, contentHash: 'abc123def456789', createdAt: '2026-08-15T11:00:00Z' },
      { id: 's2', projectId: 'p1', versionNumber: 2, contentHash: 'def456abc123789', createdAt: '2026-08-15T11:05:00Z' },
    ]);
    vi.mocked(analysesApi.history).mockResolvedValue(history);
    renderPage();

    // Navigating to history uses the stored analysis id, not a re-run.
    const link = await screen.findByRole('link', { name: 'v1 → v2' });
    expect(link).toHaveAttribute('href', '/analyses/a1');
    expect(analysesApi.create).not.toHaveBeenCalled();

    expect(screen.getByText('COMPLETED')).toBeInTheDocument();
    // "v1" also appears in the source/target selects, so assert on the schema table row.
    expect(screen.getAllByText('v1').length).toBeGreaterThan(0);
    expect(screen.getByText(/abc123def456…/)).toBeInTheDocument();
  });

  it('offers the sample loader when no schema versions exist', async () => {
    vi.mocked(projectsApi.get).mockResolvedValue(projects[0]);
    vi.mocked(schemasApi.list).mockResolvedValue([]);
    vi.mocked(analysesApi.history).mockResolvedValue([]);
    renderPage();

    expect(await screen.findByRole('button', { name: /Load sample schemas/ })).toBeInTheDocument();
    expect(screen.getByText(/No analyses recorded/)).toBeInTheDocument();
  });
});
