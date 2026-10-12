import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { JOB_ID, marketDataJobs, page } from '@/test/adminFixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

describe('AdminJobsPage', () => {
  it('lists jobs with status, progress of running jobs and record counts', async () => {
    signInAsAdmin();
    renderApp('/app/admin/sync-jobs');

    const table = await screen.findByRole('table', { name: 'Sync jobs' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);

    expect(within(rows[1]).getByText('Running')).toBeInTheDocument();
    expect(within(rows[1]).getByRole('link', { name: 'Backfill' })).toHaveAttribute('href', `/app/admin/sync-jobs/${JOB_ID}`);
    const progress = within(rows[1]).getByRole('progressbar');
    expect(progress).toHaveAttribute('aria-valuenow', '40');
    expect(rows[1]).toHaveTextContent('40%');

    expect(within(rows[2]).getByText('Completed')).toBeInTheDocument();
    expect(within(rows[2]).queryByRole('progressbar')).not.toBeInTheDocument();
    expect(rows[2]).toHaveTextContent('5 fetched · 1 inserted · 0 updated · 4 skipped');
    expect(rows[2]).toHaveTextContent('Schedule');

    expect(within(rows[3]).getByText('Failed')).toBeInTheDocument();
    expect(rows[3]).toHaveTextContent('NVDA');
  });

  it('starts with the status filter from the link and sends every filter', async () => {
    signInAsAdmin();
    const requests: URLSearchParams[] = [];
    server.use(http.get(api('/admin/market-data/jobs'), ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json(page(marketDataJobs.filter((job) => job.status === 'FAILED')));
    }));
    const { user } = renderApp('/app/admin/sync-jobs?status=FAILED');

    await screen.findByRole('table', { name: 'Sync jobs' });
    expect(requests[0].get('status')).toBe('FAILED');
    expect(screen.getByRole('combobox', { name: /^Status/ })).toHaveTextContent('Failed');

    await user.click(screen.getByRole('combobox', { name: /^Type/ }));
    await user.click(await screen.findByRole('option', { name: 'Initial load' }));
    await waitFor(() => expect(requests.at(-1)?.get('type')).toBe('INITIAL_LOAD'));

    await user.click(screen.getByRole('combobox', { name: /^Trigger/ }));
    await user.click(await screen.findByRole('option', { name: 'Watchlist' }));
    await waitFor(() => expect(requests.at(-1)?.get('trigger')).toBe('WATCHLIST'));

    await user.type(screen.getByRole('searchbox', { name: 'Symbol' }), 'nvda');
    await waitFor(() => expect(requests.at(-1)?.get('symbol')).toBe('NVDA'));
    expect(requests.at(-1)?.get('status')).toBe('FAILED');
  });

  it('keeps polling while jobs are running', async () => {
    signInAsAdmin();
    let calls = 0;
    server.use(http.get(api('/admin/market-data/jobs'), () => {
      calls += 1;
      const running = { ...marketDataJobs[0], progress: calls === 1 ? 40 : 100, status: calls === 1 ? 'RUNNING' : 'COMPLETED' };
      return HttpResponse.json(page([running]));
    }));
    renderApp('/app/admin/sync-jobs');

    expect(await screen.findByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
    expect(await screen.findByText('Completed', {}, { timeout: 8_000 })).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('explains an empty job list', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/market-data/jobs'), () => HttpResponse.json(page([]))));
    renderApp('/app/admin/sync-jobs');

    expect(await screen.findByText('No sync jobs yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Manage market data' })).toHaveAttribute('href', '/app/admin/market-data');
  });
});
