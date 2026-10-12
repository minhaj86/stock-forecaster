import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { BackfillRequest, RepopulateRequest } from '@/models/admin';
import { adminStock, JOB_ID } from '@/test/adminFixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

const valueOf = (region: HTMLElement, label: string) => within(region).getByText(label).nextElementSibling?.textContent;

describe('AdminStockDetailsPage', () => {
  it('shows the stock metadata and its stored market data', async () => {
    signInAsAdmin();
    renderApp('/app/admin/stocks/AAPL');

    expect(await screen.findByRole('heading', { name: 'AAPL · Apple Inc.', level: 1 })).toBeInTheDocument();
    const details = screen.getByRole('region', { name: 'Details' });
    expect(valueOf(details, 'Status')).toBe('Active');
    expect(valueOf(details, 'MIC')).toBe('XNAS');
    expect(valueOf(details, 'Source')).toBe('Seeded catalogue');

    const marketData = screen.getByRole('region', { name: 'Market data' });
    expect(within(marketData).getByText('Data ready')).toBeInTheDocument();
    expect(valueOf(marketData, 'Data range')).toBe('Oct 11, 2021 → Oct 9, 2026');
    expect(valueOf(marketData, 'Records')).toBe('1,258');
    expect(valueOf(marketData, 'Provider')).toBe('twelve-data');
  });

  it('queues a latest-data update without confirmation', async () => {
    signInAsAdmin();
    let updates = 0;
    server.use(http.post(api('/admin/stocks/:symbol/market-data/update'), () => {
      updates += 1;
      return HttpResponse.json({ job: { id: 'f82d1a2b-1111-7a51-9f43-2b9c7a0c1b11', status: 'QUEUED' }, created: true }, { status: 202 });
    }));
    const { user, router } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Update latest data' }));

    expect(await screen.findByText('Latest-data update queued for AAPL. Job #f82d1a2b')).toBeInTheDocument();
    expect(updates).toBe(1);
    await user.click(screen.getByRole('button', { name: 'View job' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/sync-jobs/f82d1a2b-1111-7a51-9f43-2b9c7a0c1b11'));
  });

  it('says so when an update is already queued', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/stocks/:symbol/market-data/update'), () => HttpResponse.json(
      { job: { id: 'f82d1a2b-1111-7a51-9f43-2b9c7a0c1b11', status: 'QUEUED' }, created: false }, { status: 202 })));
    const { user } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Update latest data' }));

    expect(await screen.findByText('A latest-data update for AAPL is already queued. Job #f82d1a2b')).toBeInTheDocument();
  });

  it('starts a backfill for a chosen range, skipping existing records by default', async () => {
    signInAsAdmin();
    let body: BackfillRequest | undefined;
    server.use(http.post(api('/admin/stocks/:symbol/market-data/backfill'), async ({ request }) => {
      body = (await request.json()) as BackfillRequest;
      return HttpResponse.json({ job: { id: '3c4d5e6f-aapl-7a51-9f43-2b9c7a0c1b11', status: 'QUEUED' }, created: true }, { status: 202 });
    }));
    const { user, router } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Backfill historical data' }));
    const dialog = await screen.findByRole('dialog', { name: 'Backfill historical data' });
    expect(within(dialog).getByLabelText('Stock')).toHaveValue('AAPL – Apple Inc.');
    expect(within(dialog).getByRole('radio', { name: 'Skip existing records' })).toBeChecked();
    expect(within(dialog).getByRole('combobox', { name: /^Interval/ })).toHaveTextContent('Daily');

    const from = within(dialog).getByLabelText('From');
    const to = within(dialog).getByLabelText('To');
    await user.clear(from);
    await user.type(from, '2015-01-01');
    await user.clear(to);
    await user.type(to, '2020-12-31');
    await user.click(within(dialog).getByRole('button', { name: 'Start backfill' }));

    expect(await screen.findByText('Historical data backfill started. AAPL 2015-01-01 → 2020-12-31 · Job #3c4d5e6f'))
      .toBeInTheDocument();
    expect(body).toEqual({ fromDate: '2015-01-01', toDate: '2020-12-31', interval: 'ONE_DAY', existingDataStrategy: 'SKIP' });
    await user.click(await screen.findByRole('button', { name: 'View job' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/sync-jobs/3c4d5e6f-aapl-7a51-9f43-2b9c7a0c1b11'));
  });

  it('rejects backfill ranges that end in the future or before they start', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Backfill historical data' }));
    const dialog = await screen.findByRole('dialog', { name: 'Backfill historical data' });
    const from = within(dialog).getByLabelText('From');
    const to = within(dialog).getByLabelText('To');
    await user.clear(to);
    await user.type(to, '2999-01-01');
    await user.click(within(dialog).getByRole('button', { name: 'Start backfill' }));
    expect(await within(dialog).findByText('Choose today or an earlier date')).toBeInTheDocument();

    await user.clear(from);
    await user.type(from, '2020-01-02');
    await user.clear(to);
    await user.type(to, '2020-01-01');
    await user.click(within(dialog).getByRole('button', { name: 'Start backfill' }));
    expect(await within(dialog).findByText('Choose a date on or after the start date')).toBeInTheDocument();
  });

  it('re-populates data only after REPOPULATE has been typed', async () => {
    signInAsAdmin();
    let body: RepopulateRequest | undefined;
    server.use(http.post(api('/admin/stocks/:symbol/market-data/repopulate'), async ({ request }) => {
      body = (await request.json()) as RepopulateRequest;
      return HttpResponse.json({ job: { id: '4d5e6f70-aapl-7a51-9f43-2b9c7a0c1b11', status: 'QUEUED' }, created: true }, { status: 202 });
    }));
    const { user } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Re-populate all data' }));
    const dialog = await screen.findByRole('dialog', { name: 'Re-populate historical data?' });
    expect(valueOf(dialog, 'Stock')).toBe('AAPL – Apple Inc.');
    expect(valueOf(dialog, 'Existing records')).toBe('1,258');
    expect(valueOf(dialog, 'Current data range')).toBe('Oct 11, 2021 → Oct 9, 2026');
    expect(dialog).toHaveTextContent('This operation will replace existing market data and recalculate analytics and forecasts. If the provider fails, existing data is kept.');
    expect(within(dialog).getByLabelText('From')).toHaveValue('2021-10-11');
    expect(within(dialog).getByLabelText('To')).toHaveValue('2026-10-09');

    const confirm = within(dialog).getByRole('button', { name: 'Re-populate data' });
    expect(confirm).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Type REPOPULATE to confirm'), 'repopulate');
    expect(confirm).toBeDisabled();
    await user.clear(within(dialog).getByLabelText('Type REPOPULATE to confirm'));
    await user.type(within(dialog).getByLabelText('Type REPOPULATE to confirm'), 'REPOPULATE');
    expect(body).toBeUndefined();
    await user.click(confirm);

    expect(await screen.findByText('Re-population started. AAPL 2021-10-11 → 2026-10-09 · Job #4d5e6f70')).toBeInTheDocument();
    expect(body).toEqual({ fromDate: '2021-10-11', toDate: '2026-10-09', interval: 'ONE_DAY' });
  });

  it('follows a running job until it has finished', async () => {
    signInAsAdmin();
    let calls = 0;
    server.use(http.get(api('/admin/stocks/MSFT'), () => {
      calls += 1;
      return HttpResponse.json(adminStock('MSFT', {
        marketData: { activeJob: calls === 1 ? { id: JOB_ID, type: 'BACKFILL', status: 'RUNNING', progress: 40 } : null },
      }));
    }));
    renderApp('/app/admin/stocks/MSFT');

    const progress = await screen.findByRole('progressbar', { name: 'Backfill progress' });
    expect(progress).toHaveAttribute('aria-valuenow', '40');
    expect(within(screen.getByRole('region', { name: 'Market data' })).getByRole('status')).toHaveTextContent('Backfill running · 40%');
    expect(screen.getByRole('link', { name: 'View job' })).toHaveAttribute('href', `/app/admin/sync-jobs/${JOB_ID}`);

    await waitFor(() => expect(screen.queryByRole('progressbar', { name: 'Backfill progress' })).not.toBeInTheDocument(),
      { timeout: 6_000 });
    expect(calls).toBeGreaterThanOrEqual(2);
  });

  it('deactivates a stock after explaining the consequences', async () => {
    signInAsAdmin();
    let deactivated = false;
    server.use(http.post(api('/admin/stocks/:symbol/deactivate'), () => {
      deactivated = true;
      return HttpResponse.json(adminStock('AAPL', { active: false }));
    }));
    const { user } = renderApp('/app/admin/stocks/AAPL');

    await user.click(await screen.findByRole('button', { name: 'Deactivate' }));
    const dialog = await screen.findByRole('dialog', { name: 'Deactivate AAPL?' });
    expect(dialog).toHaveTextContent('hides the stock from search and stops automatic synchronisation');
    expect(deactivated).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    expect(await screen.findByText('Deactivated AAPL. It is hidden from search and no longer synchronized.')).toBeInTheDocument();
    expect(deactivated).toBe(true);
  });

  it('disables data actions for inactive stocks and activates them', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/stocks/KO');

    expect(await screen.findByText('This stock is inactive. Activate it to update or load its market data.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Update latest data' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Populate data' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Re-populate all data' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Activate' }));
    expect(await screen.findByText('Activated KO. It can be found again and is synchronized automatically.')).toBeInTheDocument();
  });

  it('explains unknown symbols', async () => {
    signInAsAdmin();
    renderApp('/app/admin/stocks/NOPE');

    expect(await screen.findByText('No stock with symbol “NOPE”')).toBeInTheDocument();
  });
});
