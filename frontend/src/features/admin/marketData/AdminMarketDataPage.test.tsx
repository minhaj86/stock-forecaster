import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { BulkLoadRequest } from '@/models/admin';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

describe('AdminMarketDataPage', () => {
  it('summarises the provider and shows the market data of the chosen stock', async () => {
    signInAsAdmin();
    const { user, router } = renderApp('/app/admin/market-data');

    const provider = await screen.findByRole('region', { name: 'Market-data provider' });
    expect(await within(provider).findByText('Twelve Data')).toBeInTheDocument();
    expect(within(provider).getByText('Healthy')).toBeInTheDocument();
    expect(within(provider).getByText('Last synchronization')).toBeInTheDocument();
    expect(await screen.findByText('No stock selected')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Stock' }));
    await user.click(await screen.findByRole('option', { name: 'AAPL – Apple Inc.' }));

    await waitFor(() => expect(router.state.location.search).toBe('?symbol=AAPL'));
    const panel = await screen.findByRole('region', { name: 'AAPL · Apple Inc.' });
    expect(within(panel).getByText('1,258')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Update latest data' })).toBeEnabled();
  });

  it('opens the stock from the link', async () => {
    signInAsAdmin();
    renderApp('/app/admin/market-data?symbol=MSFT');

    const panel = await screen.findByRole('region', { name: 'MSFT · Microsoft Corporation' });
    expect(within(panel).getByRole('progressbar', { name: 'Backfill progress' })).toHaveAttribute('aria-valuenow', '40');
    expect(screen.getByRole('combobox', { name: 'Stock' })).toHaveValue('MSFT – Microsoft Corporation');
  });

  it('queues a bulk historical load for several stocks', async () => {
    signInAsAdmin();
    let body: BulkLoadRequest | undefined;
    server.use(http.post(api('/admin/market-data/bulk-load'), async ({ request }) => {
      body = (await request.json()) as BulkLoadRequest;
      return HttpResponse.json({ batchId: 'batch-1', jobs: body.symbols.map((symbol) => ({ id: `job-${symbol}`, symbol })) },
        { status: 202 });
    }));
    const { user, router } = renderApp('/app/admin/market-data');

    await user.click(await screen.findByRole('button', { name: 'Bulk historical load' }));
    const dialog = await screen.findByRole('dialog', { name: 'Bulk historical load' });
    const stocks = within(dialog).getByRole('combobox', { name: 'Stocks' });
    await user.click(stocks);
    await user.click(await screen.findByRole('option', { name: /^AAPL/ }));
    await user.click(stocks);
    await user.click(await screen.findByRole('option', { name: /^MSFT/ }));
    expect(within(dialog).queryByRole('option', { name: /^KO/ })).not.toBeInTheDocument();

    const from = within(dialog).getByLabelText('From');
    const to = within(dialog).getByLabelText('To');
    await user.clear(from);
    await user.type(from, '2018-01-01');
    await user.clear(to);
    await user.type(to, '2020-12-31');
    await user.click(within(dialog).getByRole('radio', { name: 'Update existing records' }));
    await user.click(within(dialog).getByRole('button', { name: 'Queue backfill jobs' }));

    expect(await screen.findByText('Queued 2 backfill jobs for 2018-01-01 → 2020-12-31')).toBeInTheDocument();
    expect(body).toEqual({
      symbols: ['AAPL', 'MSFT'], fromDate: '2018-01-01', toDate: '2020-12-31', interval: 'ONE_DAY', existingDataStrategy: 'UPDATE',
    });
    await user.click(await screen.findByRole('button', { name: 'View jobs' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/sync-jobs'));
  });

  it('requires at least one stock for a bulk load', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/market-data');

    await user.click(await screen.findByRole('button', { name: 'Bulk historical load' }));
    const dialog = await screen.findByRole('dialog', { name: 'Bulk historical load' });
    await user.click(within(dialog).getByRole('button', { name: 'Queue backfill jobs' }));

    expect(await within(dialog).findByText('Choose at least one stock')).toBeInTheDocument();
  });
});
