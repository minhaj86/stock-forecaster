import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { stocks, watchlist } from '@/test/fixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('StockSearch', () => {
  it('debounces typing into a single request and opens the chosen stock', async () => {
    const queries: string[] = [];
    server.use(http.get(api('/stocks/search'), ({ request }) => {
      queries.push(new URL(request.url).searchParams.get('q') ?? '');
      return HttpResponse.json([stocks.MSFT, stocks.MSTR]);
    }));
    const { user, router } = renderApp('/app/dashboard');

    await user.type(await screen.findByRole('combobox', { name: 'Search stocks' }), 'micro');

    const options = await screen.findAllByRole('option');
    expect(options[0]).toHaveTextContent('MSFT');
    expect(options[0]).toHaveTextContent('Microsoft Corporation');
    expect(queries).toEqual(['micro']);

    await user.click(options[0]);
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/stocks/MSFT'));
    expect(await screen.findByRole('heading', { name: 'Microsoft Corporation' })).toBeInTheDocument();
  });

  it('adds a result to a watchlist without leaving the page', async () => {
    let added: { watchlist?: string; body?: unknown } = {};
    server.use(http.post(api('/watchlists/:id/items'), async ({ params, request }) => {
      added = { watchlist: String(params.id), body: await request.json() };
      return HttpResponse.json({ stock: stocks.KO, addedAt: '2026-10-11T12:00:00Z', notes: null }, { status: 201 });
    }));
    const { user, router } = renderApp('/app/dashboard');

    await user.type(await screen.findByRole('combobox', { name: 'Search stocks' }), 'coca');
    await user.click(await screen.findByRole('button', { name: 'Add KO to a watchlist' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add KO to a watchlist' });
    await user.click(await within(dialog).findByRole('button', { name: 'Add KO to Technology' }));

    await waitFor(() => expect(added).toEqual({ watchlist: 'wl-tech', body: { symbol: 'KO' } }));
    expect(await screen.findByText('Added KO to Technology')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/dashboard');
  });

  it('shows which watchlists already contain the stock', async () => {
    const { user } = renderApp('/app/dashboard');

    await user.type(await screen.findByRole('combobox', { name: 'Search stocks' }), 'MSFT');
    await user.click(await screen.findByRole('button', { name: 'Add MSFT to a watchlist' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add MSFT to a watchlist' });

    await waitFor(() => expect(within(dialog).getAllByRole('button', { name: 'Added' })).toHaveLength(2));
    expect(watchlist().items).toHaveLength(3);
  });

  it('tells the user when nothing matches', async () => {
    server.use(http.get(api('/stocks/search'), () => HttpResponse.json([])));
    const { user } = renderApp('/app/dashboard');

    await user.type(await screen.findByRole('combobox', { name: 'Search stocks' }), 'zzzz');

    expect(await screen.findByText('No matching stocks')).toBeInTheDocument();
  });
});
