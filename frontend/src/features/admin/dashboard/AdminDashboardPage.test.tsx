import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { adminDashboard } from '@/test/adminFixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

/** The value shown for a label in a description list. */
const valueOf = (region: HTMLElement, label: string) => within(region).getByText(label).nextElementSibling?.textContent;

describe('AdminDashboardPage', () => {
  it('summarises users, stocks, market data and jobs', async () => {
    signInAsAdmin();
    renderApp('/app/admin');

    const users = await screen.findByRole('region', { name: 'Users' });
    expect(valueOf(users, 'Total')).toBe('128');
    expect(valueOf(users, 'Active')).toBe('121');
    expect(valueOf(users, 'Disabled')).toBe('6');
    expect(valueOf(users, 'Locked')).toBe('1');
    expect(valueOf(users, 'Administrators')).toBe('2');
    expect(valueOf(users, 'New (7 days)')).toBe('12');
    expect(within(users).getByRole('link', { name: 'Manage users' })).toHaveAttribute('href', '/app/admin/users');

    const stocks = screen.getByRole('region', { name: 'Stocks' });
    expect(valueOf(stocks, 'Catalogue')).toBe('69');
    expect(valueOf(stocks, 'Tracked')).toBe('40');
    expect(valueOf(stocks, 'With market data')).toBe('38');
    expect(valueOf(stocks, 'Missing data')).toBe('2');
    expect(within(stocks).getByRole('link', { name: 'Manage stocks' })).toHaveAttribute('href', '/app/admin/stocks');

    const marketData = screen.getByRole('region', { name: 'Market data' });
    expect(within(marketData).getByText('Twelve Data')).toBeInTheDocument();
    expect(within(marketData).getByText('Healthy')).toBeInTheDocument();
    expect(within(marketData).getByText('Market-data provider timed out')).toBeInTheDocument();

    const jobs = screen.getByRole('region', { name: 'Jobs' });
    expect(valueOf(jobs, 'Queued')).toBe('0');
    expect(valueOf(jobs, 'Running')).toBe('1');
    expect(valueOf(jobs, 'Completed today')).toBe('48');
    expect(valueOf(jobs, 'Failed today')).toBe('2');
    expect(within(jobs).getByRole('link', { name: 'View failed jobs' })).toHaveAttribute('href', '/app/admin/sync-jobs?status=FAILED');
  });

  it('triggers a synchronization and links to the queued jobs', async () => {
    signInAsAdmin();
    let triggered = 0;
    server.use(http.post(api('/admin/market-data/synchronize'), () => {
      triggered += 1;
      return HttpResponse.json({ stocksConsidered: 40, jobsQueued: 12 }, { status: 202 });
    }));
    const { user, router } = renderApp('/app/admin');

    await user.click(await screen.findByRole('button', { name: 'Trigger synchronization' }));

    expect(await screen.findByText('Queued 12 updates for 40 watched stocks')).toBeInTheDocument();
    expect(triggered).toBe(1);
    await user.click(screen.getByRole('button', { name: 'View jobs' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/sync-jobs'));
  });

  it('explains when nothing needed to be queued', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/market-data/synchronize'),
      () => HttpResponse.json({ stocksConsidered: 3, jobsQueued: 0 }, { status: 202 })));
    const { user } = renderApp('/app/admin');

    await user.click(await screen.findByRole('button', { name: 'Trigger synchronization' }));

    expect(await screen.findByText('No updates queued: all 3 watched stocks already have a queued or running job.'))
      .toBeInTheDocument();
  });

  it('shows the backend message and recovers on retry', async () => {
    signInAsAdmin();
    let calls = 0;
    server.use(http.get(api('/admin/dashboard'), () => (calls++ === 0
      ? apiError(503, 'SERVICE_UNAVAILABLE', 'The overview is temporarily unavailable.')
      : HttpResponse.json(adminDashboard()))));
    const { user } = renderApp('/app/admin');

    expect(await screen.findByText('Unable to load the admin overview')).toBeInTheDocument();
    expect(screen.getByText('The overview is temporarily unavailable.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('region', { name: 'Users' })).toBeInTheDocument();
  });
});
