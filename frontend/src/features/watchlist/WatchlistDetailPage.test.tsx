import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { snapshot, stocks, watchlist } from '@/test/fixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

const RETURNS: Record<string, number> = { AAPL: 0.05, MSFT: 0.18, NVDA: -0.07 };

describe('WatchlistDetailPage', () => {
  it('shows the stocks with metrics and sorts by monthly return', async () => {
    server.use(http.get(api('/analytics/snapshots'), ({ request }) => {
      const symbols = new URL(request.url).searchParams.get('symbols')?.split(',') ?? [];
      return HttpResponse.json(symbols.map((symbol) => snapshot(symbol, { return1M: RETURNS[symbol] })));
    }));
    const { user } = renderApp('/app/watchlists/wl-main');

    expect(await screen.findByRole('heading', { name: 'My Watchlist' })).toBeInTheDocument();
    const table = await screen.findByRole('table', { name: 'Stocks in My Watchlist' });
    await waitFor(() => expect(within(table).getAllByText('$100.00')).toHaveLength(3));

    await user.click(within(table).getByRole('button', { name: '1M' }));
    const symbols = () => within(table).getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell')[0].textContent);
    expect(symbols()).toEqual(['MSFT', 'AAPL', 'NVDA']);

    await user.click(within(table).getByRole('button', { name: '1M' }));
    expect(symbols()).toEqual(['NVDA', 'AAPL', 'MSFT']);
  });

  it('removes a stock and offers to undo', async () => {
    let removed: string | undefined;
    server.use(http.delete(api('/watchlists/:id/items/:symbol'), ({ params }) => {
      removed = String(params.symbol);
      return new HttpResponse(null, { status: 204 });
    }));
    const { user } = renderApp('/app/watchlists/wl-main');

    await user.click(await screen.findByRole('button', { name: 'Remove NVDA from My Watchlist' }));

    await waitFor(() => expect(removed).toBe('NVDA'));
    expect(await screen.findByText('Removed NVDA from My Watchlist')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument();
  });

  it('adds a stock through search', async () => {
    let added: unknown;
    server.use(http.post(api('/watchlists/:id/items'), async ({ request }) => {
      added = await request.json();
      const stock = stocks.KO;
      return HttpResponse.json({ stock: { ...stock }, addedAt: '2026-10-11T12:00:00Z', notes: null }, { status: 201 });
    }));
    const { user } = renderApp('/app/watchlists/wl-main');

    await user.click(await screen.findByRole('button', { name: 'Add stock' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add stocks to My Watchlist' });
    await user.type(within(dialog).getByLabelText('Search by ticker or company'), 'coca');

    await user.click(await within(dialog).findByRole('button', { name: 'Add KO' }));

    await waitFor(() => expect(added).toEqual({ symbol: 'KO' }));
    expect(await screen.findByText('Added KO to My Watchlist')).toBeInTheDocument();
  });

  it('marks stocks that are already in the list', async () => {
    const { user } = renderApp('/app/watchlists/wl-main');

    await user.click(await screen.findByRole('button', { name: 'Add stock' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add stocks to My Watchlist' });
    await user.type(within(dialog).getByLabelText('Search by ticker or company'), 'MSFT');

    expect(await within(dialog).findByRole('button', { name: 'Added' })).toBeDisabled();
  });

  it('explains when a watchlist does not exist or belongs to someone else', async () => {
    renderApp('/app/watchlists/someone-elses');

    expect(await screen.findByText('Watchlist not found')).toBeInTheDocument();
  });

  it('shows an empty state for an empty list', async () => {
    server.use(http.get(api('/watchlists/:id'), () => HttpResponse.json(watchlist('wl-empty', 'Empty', []))));
    renderApp('/app/watchlists/wl-empty');

    expect(await screen.findByText('This watchlist is empty')).toBeInTheDocument();
  });
});
