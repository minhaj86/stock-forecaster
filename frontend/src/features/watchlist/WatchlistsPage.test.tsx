import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { watchlist, watchlistSummaries } from '@/test/fixtures';
import { api } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

describe('WatchlistsPage', () => {
  it('lists the watchlists with their stocks', async () => {
    renderApp('/app/watchlists');

    const list = await screen.findByRole('list', { name: 'Watchlists' });
    const cards = within(list).getAllByRole('listitem');
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveTextContent('My Watchlist');
    expect(cards[0]).toHaveTextContent('3 stocks');
    expect(within(cards[0]).getByText('NVDA')).toBeInTheDocument();
    expect(cards[1]).toHaveTextContent('1 stock');
  });

  it('creates a watchlist', async () => {
    let created: unknown;
    server.use(http.post(api('/watchlists'), async ({ request }) => {
      created = await request.json();
      return HttpResponse.json(watchlist('wl-new', 'Dividends', []), { status: 201 });
    }));
    const { user } = renderApp('/app/watchlists');

    await user.click(await screen.findByRole('button', { name: 'Create watchlist' }));
    const dialog = await screen.findByRole('dialog', { name: 'Create watchlist' });
    await user.type(within(dialog).getByLabelText('Name'), 'Dividends');
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await screen.findByText('Created Dividends')).toBeInTheDocument();
    expect(created).toEqual({ name: 'Dividends' });
  });

  it('shows the server message when a name is taken', async () => {
    server.use(http.post(api('/watchlists'), () => HttpResponse.json({
      status: 409, code: 'WATCHLIST_NAME_TAKEN', message: "You already have a watchlist named 'Technology'",
    }, { status: 409 })));
    const { user } = renderApp('/app/watchlists');

    await user.click(await screen.findByRole('button', { name: 'Create watchlist' }));
    const dialog = await screen.findByRole('dialog', { name: 'Create watchlist' });
    await user.type(within(dialog).getByLabelText('Name'), 'Technology');
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText("You already have a watchlist named 'Technology'")).toBeInTheDocument();
  });

  it('deletes a watchlist only after confirmation', async () => {
    let deleted: string | undefined;
    server.use(http.delete(api('/watchlists/:id'), ({ params }) => {
      deleted = String(params.id);
      return new HttpResponse(null, { status: 204 });
    }));
    const { user } = renderApp('/app/watchlists');

    await user.click(await screen.findByRole('button', { name: 'Actions for Technology' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete Technology?' });
    expect(deleted).toBeUndefined();

    server.use(http.get(api('/watchlists'), () => HttpResponse.json([watchlistSummaries[0]])));
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(deleted).toBe('wl-tech'));
    expect(await screen.findByText('Deleted Technology')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(1));
  });

  it('offers to create the first watchlist', async () => {
    server.use(http.get(api('/watchlists'), () => HttpResponse.json([])));
    renderApp('/app/watchlists');

    expect(await screen.findByText('No watchlists yet')).toBeInTheDocument();
  });
});
