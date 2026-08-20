import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectsPage } from '../pages/ProjectsPage';
import { ApiError } from '../api/client';
import { projects } from './fixtures';

vi.mock('../api/projects', () => ({
  projectsApi: { list: vi.fn(), create: vi.fn(), get: vi.fn() },
}));
const { projectsApi } = await import('../api/projects');

function renderPage() {
  return render(
    <MemoryRouter>
      <ProjectsPage />
    </MemoryRouter>,
  );
}

describe('ProjectsPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('renders the thesis and the project list', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue(projects);
    renderPage();

    expect(
      screen.getByText('A compatibility PASS does not imply operational safety.'),
    ).toBeInTheDocument();
    expect(await screen.findByText('E-commerce Orders')).toBeInTheDocument();
    expect(screen.getByText('Payments')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /E-commerce Orders/ })).toHaveAttribute(
      'href',
      '/projects/p1',
    );
  });

  it('shows an empty state when there are no projects', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/No projects yet/)).toBeInTheDocument();
  });

  it('reports an unreachable backend', async () => {
    vi.mocked(projectsApi.list).mockRejectedValue(ApiError.unreachable());
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent(/Cannot reach the ContractGuard API/);
  });

  it('displays an RFC 9457 validation error with field detail', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue([]);
    vi.mocked(projectsApi.create).mockRejectedValue(
      new ApiError('The request body is invalid', 400, {
        status: 400,
        title: 'Validation failed',
        detail: 'The request body is invalid',
        errors: { name: 'must not be blank' },
      }),
    );
    renderPage();
    await screen.findByText(/No projects yet/);

    await userEvent.type(screen.getByRole('textbox', { name: /Name/ }), 'x');
    await userEvent.click(screen.getByRole('button', { name: /Create project/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Validation failed');
    expect(alert).toHaveTextContent('400');
    expect(alert).toHaveTextContent('must not be blank');
  });
});
