import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AnalysisPage } from '../pages/AnalysisPage';
import { ApiError } from '../api/client';
import { analysisRun, rolloutPlan } from './fixtures';

vi.mock('../api/analyses', () => ({
  analysesApi: { get: vi.fn(), create: vi.fn(), history: vi.fn() },
}));
vi.mock('../api/rollout', () => ({ rolloutApi: { get: vi.fn() } }));
const { analysesApi } = await import('../api/analyses');
const { rolloutApi } = await import('../api/rollout');

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/analyses/a1']}>
      <Routes>
        <Route path="/analyses/:analysisId" element={<AnalysisPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AnalysisPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('renders BACKWARD PASS and FORWARD FAIL with the failing path', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue(analysisRun);
    vi.mocked(rolloutApi.get).mockResolvedValue(rolloutPlan);
    renderPage();

    expect(await screen.findByText('BACKWARD')).toBeInTheDocument();
    expect(screen.getByText('FORWARD')).toBeInTheDocument();
    expect(screen.getByText('FULL')).toBeInTheDocument();

    expect(screen.getAllByText('PASS')).toHaveLength(1);
    expect(screen.getAllByText('FAIL')).toHaveLength(2);

    expect(screen.getByText('TYPE_MISMATCH')).toBeInTheDocument();
    expect(screen.getByText('OrderEvent.customerEmail')).toBeInTheDocument();
  });

  it('renders the operational-risk finding independently of compatibility', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue(analysisRun);
    vi.mocked(rolloutApi.get).mockResolvedValue(rolloutPlan);
    renderPage();

    expect(await screen.findByText('ENUM_SEMANTIC_FALLBACK_RISK')).toBeInTheDocument();
    // Also named in the rollout steps, so scope to the finding's own element.
    expect(document.querySelector('.finding .consumer')).toHaveTextContent(
      'order-notification-service',
    );
    // Once as the overall severity, once on the finding itself.
    expect(screen.getAllByText('HIGH')).toHaveLength(2);
    expect(screen.getByText('OrderEvent.status')).toBeInTheDocument();
    // RETURNED -> CREATED fallback is spelled out.
    expect(screen.getByText('RETURNED')).toBeInTheDocument();
    expect(screen.getByText('CREATED')).toBeInTheDocument();
  });

  it('renders source evidence with file, line and code excerpt', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue(analysisRun);
    vi.mocked(rolloutApi.get).mockResolvedValue(rolloutPlan);
    renderPage();

    expect(await screen.findByText('OrderStatusHandler.java:20')).toBeInTheDocument();
    expect(
      screen.getByText('case CREATED -> sendNewOrderNotification(order);'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('order-notification-service/OrderStatusHandler.java'),
    ).toBeInTheDocument();
  });

  it('renders ordered rollout steps and limitations', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue(analysisRun);
    vi.mocked(rolloutApi.get).mockResolvedValue(rolloutPlan);
    renderPage();

    expect(await screen.findByText('Consumer first')).toBeInTheDocument();
    expect(screen.getByText('UPDATE_CONSUMER')).toBeInTheDocument();
    expect(screen.getByText('VERIFY_CONSUMER_DEPLOYMENT')).toBeInTheDocument();
    expect(screen.getByText('DEPLOY_SCHEMA')).toBeInTheDocument();
    expect(screen.getByText(/Guidance is based only on the rules/)).toBeInTheDocument();
  });

  it('never labels NO_CONSTRAINT_IDENTIFIED as safe', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue(analysisRun);
    vi.mocked(rolloutApi.get).mockResolvedValue({
      ...rolloutPlan,
      strategy: 'NO_CONSTRAINT_IDENTIFIED',
      summary: 'No rollout constraint was identified by the currently implemented rules.',
      steps: [],
    });
    renderPage();

    expect(await screen.findByText('No rollout constraint identified')).toBeInTheDocument();
    expect(screen.queryByText(/safe to deploy/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Safe$/i)).not.toBeInTheDocument();
  });

  it('explains a 409 rollout instead of showing a raw error', async () => {
    vi.mocked(analysesApi.get).mockResolvedValue({ ...analysisRun, status: 'FAILED' });
    vi.mocked(rolloutApi.get).mockRejectedValue(
      new ApiError('Analysis is FAILED', 409, { status: 409, title: 'Conflict' }),
    );
    renderPage();

    expect(
      await screen.findByText(/Rollout guidance is only available for a COMPLETED analysis/),
    ).toBeInTheDocument();
  });

  it('shows a 404 problem response for an unknown analysis', async () => {
    vi.mocked(analysesApi.get).mockRejectedValue(
      new ApiError('Analysis not found', 404, {
        status: 404,
        title: 'Resource not found',
        detail: 'Analysis a1 not found',
      }),
    );
    vi.mocked(rolloutApi.get).mockRejectedValue(new ApiError('x', 404, null));
    renderPage();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Resource not found');
    expect(alert).toHaveTextContent('Analysis a1 not found');
  });
});
