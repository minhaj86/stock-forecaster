import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { dashboard, snapshot } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('DashboardPage', () => {
  it('summarises the watchlist with backend-computed metrics', async () => {
    renderApp('/app/dashboard');

    expect(await screen.findByRole('heading', { name: /, Ada$/ })).toBeInTheDocument();
    expect(await screen.findByText(/Last updated .* Market data as of Oct 9, 2026/)).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Watchlist summary' });
    expect(within(summary).getByText('3 stocks')).toBeInTheDocument();
    expect(within(summary).getByRole('button', { name: 'Best performer (1M): MSFT' })).toHaveTextContent('+18.40%');
    expect(within(summary).getByRole('button', { name: 'Highest volatility (30D): NVDA' })).toHaveTextContent('32.7%');

    const movers = screen.getByRole('region', { name: 'Market movers' });
    expect(within(movers).getByRole('button', { name: 'Largest decline (1M): NVDA' })).toHaveTextContent('-7.10%');
    expect(within(movers).getByRole('button', { name: 'Strongest momentum (3M): NVDA' })).toBeInTheDocument();

    const table = screen.getByRole('table', { name: 'Your watched stocks' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('AAPL');
    expect(rows[1]).toHaveTextContent('$227.52');
    expect(within(rows[3]).getByLabelText('down -0.50%')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: /Performance of AAPL, MSFT, NVDA over 1M/ })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Demo data.');
  });

  it('opens a stock when its row is clicked', async () => {
    const { user, router } = renderApp('/app/dashboard');

    const table = await screen.findByRole('table', { name: 'Your watched stocks' });
    await user.click(within(table).getByText('Microsoft Corporation'));

    await waitFor(() => expect(router.state.location.pathname).toBe('/app/stocks/MSFT'));
  });

  it('requests the selected period', async () => {
    const periods: (string | null)[] = [];
    server.use(http.get(api('/dashboard'), ({ request }) => {
      const period = new URL(request.url).searchParams.get('period');
      periods.push(period);
      return HttpResponse.json(dashboard({ period: (period ?? '1M') as '1M' }));
    }));
    const { user } = renderApp('/app/dashboard');

    await user.click(await screen.findByRole('button', { name: '6M period' }));

    await waitFor(() => expect(periods).toContain('6M'));
    expect(await screen.findByRole('button', { name: 'Best performer (6M): MSFT' })).toBeInTheDocument();
  });

  it('guides users with an empty watchlist to the stock search', async () => {
    server.use(http.get(api('/dashboard'), () => HttpResponse.json(dashboard({
      stockCount: 0, stocks: [], dataAsOf: null, lastSyncedAt: null, dataProviders: [],
      movers: { topPerformer: null, weakestPerformer: null, highestVolatility: null, lowestVolatility: null, strongestMomentum: null },
      performance: { period: '1M', asOf: null, series: [] },
    }))));
    const { user, router } = renderApp('/app/dashboard');

    expect(await screen.findByText('Your watchlist is empty')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Search stocks' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/app/stocks'));
  });

  it('explains that data for newly added stocks is still being collected', async () => {
    server.use(http.get(api('/dashboard'), () => HttpResponse.json(dashboard({
      stocks: [...dashboard().stocks, snapshot('KO', { dataStatus: 'PENDING', price: null, changePercent: null, return1M: null })],
      stockCount: 4,
    }))));
    renderApp('/app/dashboard');

    expect(await screen.findByText(/Collecting market data for KO/)).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Your watched stocks' });
    expect(within(table).getByText('Syncing…')).toBeInTheDocument();
  });

  it('shows a useful error and recovers on retry', async () => {
    let calls = 0;
    server.use(http.get(api('/dashboard'), () => (calls++ === 0
      ? apiError(503, 'MARKET_DATA_UNAVAILABLE', 'Market data is temporarily unavailable.')
      : HttpResponse.json(dashboard()))));
    const { user } = renderApp('/app/dashboard');

    const alert = await screen.findByText('Unable to load your dashboard');
    expect(alert.closest('[role="alert"]')).toHaveTextContent('Market data is temporarily unavailable.');
    expect(screen.getByText('Reference: corr-123')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('table', { name: 'Your watched stocks' })).toBeInTheDocument();
  });
});
