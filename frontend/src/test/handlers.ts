import { http, HttpResponse } from 'msw';
import { adminHandlers } from './adminHandlers';
import * as fixtures from './fixtures';
import { api, apiError } from './http';

export { api, apiError };

/** A signed-in user with two watchlists and synthetic market data. Tests override individual endpoints. */
export const defaultHandlers = [
  http.post(api('/auth/refresh'), () => HttpResponse.json(fixtures.session())),
  http.post(api('/auth/login'), () => HttpResponse.json(fixtures.session())),
  http.post(api('/auth/register'), () => HttpResponse.json(fixtures.session(), { status: 201 })),
  http.post(api('/auth/logout'), () => new HttpResponse(null, { status: 204 })),
  http.post(api('/auth/password'), () => HttpResponse.json(fixtures.session({ accessToken: 'access-token-2' }))),
  http.get(api('/users/me'), () => HttpResponse.json(fixtures.profile)),
  http.get(api('/market-data/provider'), () => HttpResponse.json({
    id: 'synthetic', name: 'Synthetic demo data', synthetic: true,
    description: 'Simulated end-of-day prices for demonstration only. They do not reflect real market prices.',
  })),
  http.get(api('/dashboard'), () => HttpResponse.json(fixtures.dashboard())),
  http.get(api('/watchlists'), () => HttpResponse.json(fixtures.watchlistSummaries)),
  http.get(api('/watchlists/:id'), ({ params }) => {
    if (params.id === 'wl-main') return HttpResponse.json(fixtures.watchlist());
    if (params.id === 'wl-tech') return HttpResponse.json(fixtures.watchlist('wl-tech', 'Technology', ['MSFT']));
    return apiError(404, 'WATCHLIST_NOT_FOUND', 'Watchlist not found');
  }),
  http.get(api('/analytics/snapshots'), ({ request }) => {
    const symbols = new URL(request.url).searchParams.get('symbols')?.split(',') ?? [];
    return HttpResponse.json(symbols.map((symbol) => fixtures.snapshot(symbol)));
  }),
  http.get(api('/stocks/search'), ({ request }) => {
    const query = (new URL(request.url).searchParams.get('q') ?? '').toUpperCase();
    const results = Object.values(fixtures.stocks).filter((stock) =>
      stock.symbol.startsWith(query) || stock.companyName.toUpperCase().includes(query));
    return HttpResponse.json(results);
  }),
  http.get(api('/stocks/sectors'), () => HttpResponse.json(['Consumer Staples', 'Information Technology'])),
  http.get(api('/stocks'), () => HttpResponse.json({
    content: Object.values(fixtures.stocks), page: { number: 0, size: 20, totalElements: 5, totalPages: 1 },
  })),
  http.get(api('/stocks/:symbol/forecast/models'), ({ params }) => HttpResponse.json(fixtures.forecastCatalog(String(params.symbol)))),
  http.get(api('/stocks/:symbol/forecast'), ({ params }) => HttpResponse.json(fixtures.forecastResult(String(params.symbol)))),
  http.get(api('/stocks/:symbol/analytics'), ({ params }) => HttpResponse.json(fixtures.snapshot(String(params.symbol)))),
  http.get(api('/stocks/:symbol/prices'), ({ params }) => HttpResponse.json({
    symbol: params.symbol, currency: 'USD', period: '1Y', interval: '1d', dataStatus: 'READY', dataProvider: 'synthetic',
    asOf: '2026-10-09', lastSyncedAt: '2026-10-11T13:42:00Z',
    bars: [
      { date: '2026-10-08', open: 98, high: 101, low: 97, close: 99, adjustedClose: 99, volume: 1000 },
      { date: '2026-10-09', open: 99, high: 102, low: 98, close: 100, adjustedClose: 100, volume: 1200 },
    ],
  })),
  http.get(api('/stocks/:symbol'), ({ params }) => {
    const stock = fixtures.stocks[String(params.symbol)];
    return stock ? HttpResponse.json(stock) : apiError(404, 'STOCK_NOT_FOUND', `No stock with symbol '${params.symbol}' is available`);
  }),
  // Admin console endpoints (only reachable with an ADMIN session, see `signInAsAdmin`).
  ...adminHandlers,
];
