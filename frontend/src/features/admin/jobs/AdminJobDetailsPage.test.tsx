import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { FAILED_JOB_ID, JOB_ID, marketDataJob, marketDataJobs } from '@/test/adminFixtures';
import { RETRIED_JOB_ID } from '@/test/adminHandlers';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';
import { formatTime } from '@/utils/format';

const valueOf = (region: HTMLElement, label: string) => within(region).getByText(label).nextElementSibling?.textContent;

function serveJob(job: ReturnType<typeof marketDataJob>) {
  server.use(http.get(api(`/admin/market-data/jobs/${job.id}`), () => HttpResponse.json(job)));
}

describe('AdminJobDetailsPage', () => {
  it('shows the progress and details of a running job', async () => {
    signInAsAdmin();
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByRole('heading', { name: 'Backfill · MSFT', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Job progress' })).toHaveAttribute('aria-valuenow', '40');
    expect(screen.getByText('Fetching 2015-01-01 → 2026-10-09 from Twelve Data')).toBeInTheDocument();
    const details = screen.getByRole('region', { name: 'Details' });
    expect(valueOf(details, 'Job ID')).toBe(JOB_ID);
    expect(valueOf(details, 'Interval')).toBe('Daily');
    expect(valueOf(details, 'Existing-data handling')).toBe('Skip existing records');
    expect(valueOf(details, 'Date range')).toBe('Jan 1, 2015 → Oct 9, 2026');
    expect(valueOf(details, 'Requested by')).toBe('ada@example.com');
    expect(valueOf(details, 'Attempts')).toBe('1 of 5');
    expect(within(details).getByRole('link', { name: 'MSFT' })).toHaveAttribute('href', '/app/admin/stocks/MSFT');
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel job' })).not.toBeInTheDocument();
  });

  it('shows the error of a failed job and retries it as a new job', async () => {
    signInAsAdmin();
    let retried: string | undefined;
    server.use(
      http.post(api('/admin/market-data/jobs/:jobId/retry'), ({ params }) => {
        retried = String(params.jobId);
        return HttpResponse.json({
          job: marketDataJob({ ...marketDataJobs[2], id: RETRIED_JOB_ID, status: 'QUEUED', retryOfJobId: FAILED_JOB_ID,
            errorCode: null, errorMessage: null, retryable: false, progress: 0 }),
          created: true,
        }, { status: 202 });
      }),
      http.get(api(`/admin/market-data/jobs/${RETRIED_JOB_ID}`), () => HttpResponse.json(marketDataJob({
        ...marketDataJobs[2], id: RETRIED_JOB_ID, status: 'QUEUED', retryOfJobId: FAILED_JOB_ID, errorCode: null,
        errorMessage: null, retryable: false, progress: 0,
      }))),
    );
    const { user, router } = renderApp(`/app/admin/sync-jobs/${FAILED_JOB_ID}`);

    expect(await screen.findByRole('heading', { name: 'Initial load · NVDA', level: 1 })).toBeInTheDocument();
    const status = screen.getByRole('region', { name: 'Status' });
    expect(within(status).getByText('Failed')).toBeInTheDocument();
    expect(within(status).getByText('The provider timed out')).toBeInTheDocument();
    expect(status).toHaveTextContent('Market-data provider timed out');
    expect(status).toHaveTextContent('Error code: PROVIDER_TIMEOUT');

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(router.state.location.pathname).toBe(`/app/admin/sync-jobs/${RETRIED_JOB_ID}`));
    expect(retried).toBe(FAILED_JOB_ID);
    expect(await screen.findByText('Retry queued for NVDA. Job #d4e5f6a7')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Job #c3d4e5f6' })).toHaveAttribute('href', `/app/admin/sync-jobs/${FAILED_JOB_ID}`);
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('offers no retry for failures that are not temporary', async () => {
    signInAsAdmin();
    serveJob(marketDataJob({
      status: 'FAILED', errorCode: 'SYMBOL_NOT_FOUND', errorMessage: 'The provider does not know MSFT', retryable: false,
      completedAt: '2026-10-11T23:03:00Z', durationMs: 59_000,
    }));
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByText('Symbol not found at the provider')).toBeInTheDocument();
    expect(screen.getByText('The provider does not know MSFT')).toBeInTheDocument();
    expect(valueOf(screen.getByRole('region', { name: 'Details' }), 'Duration')).toBe('59.0 s');
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('explains a request the provider rejected, without offering a retry', async () => {
    signInAsAdmin();
    serveJob(marketDataJob({
      status: 'FAILED', errorCode: 'PROVIDER_REJECTED_REQUEST', retryable: false, completedAt: '2026-10-11T23:03:00Z',
      errorMessage: 'The provider rejected the requested date range (start_date is after end_date)',
    }));
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByText('The provider rejected the request')).toBeInTheDocument();
    const status = screen.getByRole('region', { name: 'Status' });
    expect(status).toHaveTextContent('The provider rejected the requested date range (start_date is after end_date)');
    expect(status).toHaveTextContent('Error code: PROVIDER_REJECTED_REQUEST');
    expect(status).not.toHaveTextContent('can be retried');
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('shows error codes it does not know yet as sent, with the backend message', async () => {
    signInAsAdmin();
    server.use(http.get(api(`/admin/market-data/jobs/${JOB_ID}`), () => HttpResponse.json({
      ...marketDataJob({ status: 'FAILED', retryable: false, errorMessage: 'Daily API credits exhausted (800 of 800)' }),
      errorCode: 'PROVIDER_QUOTA_EXHAUSTED',
    })));
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByText('The job failed')).toBeInTheDocument();
    const status = screen.getByRole('region', { name: 'Status' });
    expect(status).toHaveTextContent('Daily API credits exhausted (800 of 800)');
    expect(status).toHaveTextContent('Error code: PROVIDER_QUOTA_EXHAUSTED');
    expect(within(status).getAllByText(/PROVIDER_QUOTA_EXHAUSTED/)).toHaveLength(1);
  });

  it('explains an automatic retry after an error it does not know yet', async () => {
    signInAsAdmin();
    const nextAttemptAt = '2026-10-11T23:20:00Z';
    server.use(http.get(api(`/admin/market-data/jobs/${JOB_ID}`), () => HttpResponse.json({
      ...marketDataJob({
        status: 'QUEUED', startedAt: null, progress: 0, attempts: 2, nextAttemptAt, errorMessage: 'Upstream maintenance window',
      }),
      errorCode: 'PROVIDER_MAINTENANCE',
    })));
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    const notice = await screen.findByText(/The last attempt failed \(attempt 2 of 5\)/);
    expect(notice).toHaveTextContent(`The job will retry automatically at ${formatTime(nextAttemptAt)}.`);
    expect(notice).toHaveTextContent('Upstream maintenance window');
    expect(notice).toHaveTextContent('Error code: PROVIDER_MAINTENANCE');
  });

  it('explains a job that waits for the provider rate limit and can be cancelled', async () => {
    signInAsAdmin();
    const nextAttemptAt = '2026-10-11T23:15:00Z';
    serveJob(marketDataJob({
      status: 'QUEUED', startedAt: null, progress: 0, attempts: 2, errorCode: 'PROVIDER_RATE_LIMITED',
      errorMessage: 'Rate limit of 8 requests per minute reached', nextAttemptAt,
    }));
    let cancelled = false;
    server.use(http.post(api('/admin/market-data/jobs/:jobId/cancel'), () => {
      cancelled = true;
      return HttpResponse.json(marketDataJob({ status: 'CANCELLED', progress: 0 }));
    }));
    const { user } = renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByText(`Provider rate limit reached. The job will automatically resume at ${formatTime(nextAttemptAt)}.`))
      .toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancel job' }));

    expect(await screen.findByText('Cancelled Job #f82d1a2b (Backfill, MSFT)')).toBeInTheDocument();
    expect(cancelled).toBe(true);
  });

  it('warns about records rejected in a partially completed job', async () => {
    signInAsAdmin();
    serveJob(marketDataJob({
      status: 'PARTIALLY_COMPLETED', progress: 100, recordsFetched: 1260, recordsInserted: 1255, recordsFailed: 5,
      completedAt: '2026-10-11T23:05:00Z', durationMs: 252_000,
    }));
    renderApp(`/app/admin/sync-jobs/${JOB_ID}`);

    expect(await screen.findByText(/5 records were rejected by validation; all valid records were stored\./)).toBeInTheDocument();
    const records = screen.getByRole('region', { name: 'Records' });
    expect(valueOf(records, 'Fetched')).toBe('1,260');
    expect(valueOf(records, 'Failed')).toBe('5');
    expect(valueOf(screen.getByRole('region', { name: 'Details' }), 'Duration')).toBe('4 min 12 s');
  });

  it('explains unknown jobs', async () => {
    signInAsAdmin();
    renderApp('/app/admin/sync-jobs/unknown-job');

    expect(await screen.findByText('Job not found')).toBeInTheDocument();
  });
});
