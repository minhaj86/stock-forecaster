import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { snapshot, stocks } from '@/test/fixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('StockDetailsPage', () => {
  it('shows price, change, metrics and the price chart', async () => {
    server.use(http.get(api('/stocks/:symbol/analytics'), () => HttpResponse.json(snapshot('MSFT', {
      price: 512.3, change: 4.22, changePercent: 0.0083, return1Y: 0.214, volatility30d: 0.183, rsi14: 61.4,
    }))));
    renderApp('/app/stocks/MSFT');

    expect(await screen.findByRole('heading', { name: 'Microsoft Corporation' })).toBeInTheDocument();
    expect(await screen.findByText('$512.30')).toBeInTheDocument();
    expect(screen.getByLabelText('up +$4.22 (+0.83%)')).toBeInTheDocument();
    expect(screen.getByText('18.3%')).toBeInTheDocument();
    expect(screen.getByText('61.4')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'MSFT daily closing prices for the 1Y period' })).toBeInTheDocument();
  });

  it('starts tracking an unwatched stock as soon as it is added to a watchlist', async () => {
    let watched = false;
    server.use(
      http.get(api('/stocks/:symbol/analytics'), () => HttpResponse.json(watched
        ? snapshot('KO', { price: 61.25 })
        : snapshot('KO', { dataStatus: 'NOT_TRACKED', price: null, changePercent: null }))),
      http.post(api('/watchlists/:id/items'), () => {
        watched = true;
        return HttpResponse.json({ stock: stocks.KO, addedAt: '2026-10-11T12:00:00Z', notes: null }, { status: 201 });
      }),
    );
    const { user } = renderApp('/app/stocks/KO');

    expect(await screen.findByText(/collected once it is on a watchlist/)).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Watchlist' })[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Add KO to a watchlist' });
    await user.click(await within(dialog).findByRole('button', { name: 'Add KO to My Watchlist' }));

    expect(await screen.findByText('$61.25')).toBeInTheDocument();
    expect(screen.queryByText(/collected once it is on a watchlist/)).not.toBeInTheDocument();
  });

  it('keeps polling price history while it is being collected', async () => {
    let calls = 0;
    server.use(http.get(api('/stocks/:symbol/prices'), () => {
      calls += 1;
      const ready = calls > 1;
      return HttpResponse.json({
        symbol: 'MSFT', currency: 'USD', period: '1Y', interval: '1d', dataStatus: ready ? 'READY' : 'PENDING',
        dataProvider: 'synthetic', asOf: ready ? '2026-10-09' : null, lastSyncedAt: null,
        bars: ready ? [{ date: '2026-10-09', open: 99, high: 102, low: 98, close: 100, adjustedClose: 100, volume: 1200 }] : [],
      });
    }));
    renderApp('/app/stocks/MSFT');

    expect(await screen.findByText(/Collecting price history/)).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'MSFT daily closing prices for the 1Y period' }, { timeout: 6000 }))
      .toBeInTheDocument();
  });

  it('explains unknown symbols', async () => {
    renderApp('/app/stocks/NOPE');

    expect(await screen.findByText('No stock with symbol “NOPE”')).toBeInTheDocument();
  });

  it('shows the forecast in its own tab', async () => {
    const { user, router } = renderApp('/app/stocks/MSFT');
    await screen.findByRole('heading', { name: 'Microsoft Corporation' });

    await user.click(screen.getByRole('tab', { name: 'Forecast' }));

    expect(router.state.location.search).toBe('?tab=forecast');
    expect(await screen.findByText('Median 5-day forecast')).toBeInTheDocument();
    expect(screen.getByRole('tabpanel')).toHaveTextContent(/not financial advice/);
  });
});
