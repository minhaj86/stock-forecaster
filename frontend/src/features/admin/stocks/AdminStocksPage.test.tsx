import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { CreateStockRequest } from '@/models/admin';
import { adminStock, adminStocks, marketDataJob, page } from '@/test/adminFixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

const SNOW_JOB_ID = 'aa11bb22-0000-7a51-9f43-2b9c7a0c1b11';
const snow = adminStock('SNOW', {
  companyName: 'Snowflake Inc.', exchange: 'NYSE', mic: 'XNYS', source: 'twelve-data', watchers: 0,
  marketData: { dataStatus: 'PENDING', barCount: 0, firstBarDate: null, lastBarDate: null, lastSuccessAt: null },
});

async function openAddStock(user: ReturnType<typeof renderApp>['user']) {
  await user.click(await screen.findByRole('button', { name: 'Add stock' }));
  return screen.findByRole('dialog', { name: 'Add stock' });
}

describe('AdminStocksPage', () => {
  it('lists the catalogue with stored data and synchronisation status', async () => {
    signInAsAdmin();
    renderApp('/app/admin/stocks');

    const table = await screen.findByRole('table', { name: 'Stocks' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    expect(rows[1]).toHaveTextContent('AAPL');
    expect(rows[1]).toHaveTextContent('Apple Inc.');
    expect(rows[1]).toHaveTextContent('Oct 9, 2026');
    expect(rows[1]).toHaveTextContent('1,258');
    expect(within(rows[1]).getByText('Data ready')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Backfill 40%')).toBeInTheDocument();
    expect(within(rows[3]).getByText('Inactive')).toBeInTheDocument();
    expect(within(rows[3]).getByText('Not tracked')).toBeInTheDocument();
    expect(within(rows[1]).getByRole('link', { name: 'AAPL' })).toHaveAttribute('href', '/app/admin/stocks/AAPL');
  });

  it('filters by status and search text', async () => {
    signInAsAdmin();
    const requests: URLSearchParams[] = [];
    server.use(http.get(api('/admin/stocks'), ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json(page(adminStocks));
    }));
    const { user } = renderApp('/app/admin/stocks');

    await screen.findByRole('table', { name: 'Stocks' });
    await user.click(screen.getByRole('button', { name: 'Inactive' }));
    await waitFor(() => expect(requests.at(-1)?.get('status')).toBe('INACTIVE'));
    await user.type(screen.getByRole('searchbox', { name: 'Search by symbol or company' }), 'coca');
    await waitFor(() => expect(requests.at(-1)?.get('query')).toBe('coca'));
    expect(requests.at(-1)?.get('status')).toBe('INACTIVE');
  });

  it('offers to add stocks to an empty catalogue', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/stocks'), () => HttpResponse.json(page([]))));
    renderApp('/app/admin/stocks');

    expect(await screen.findByText('The catalogue is empty')).toBeInTheDocument();
  });

  it('shows the backend message when the catalogue cannot be loaded', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/stocks'), () => apiError(500, 'INTERNAL_ERROR', 'The catalogue is unavailable.')));
    renderApp('/app/admin/stocks');

    expect(await screen.findByText('Unable to load stocks')).toBeInTheDocument();
    expect(screen.getByText('The catalogue is unavailable.')).toBeInTheDocument();
  });

  it('adds a stock found through the provider and loads its history', async () => {
    signInAsAdmin();
    let body: CreateStockRequest | undefined;
    server.use(
      http.post(api('/admin/stocks'), async ({ request }) => {
        body = (await request.json()) as CreateStockRequest;
        return HttpResponse.json({
          stock: snow,
          job: marketDataJob({ id: SNOW_JOB_ID, type: 'INITIAL_LOAD', status: 'QUEUED', symbol: 'SNOW', progress: 0 }),
        }, { status: 201 });
      }),
      http.get(api('/admin/stocks/SNOW'), () => HttpResponse.json(snow)),
    );
    const { user, router } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'snow');
    await user.click(await within(dialog).findByRole('radio', { name: /SNOW · Snowflake Inc\./ }));
    expect(within(dialog).getByRole('checkbox', { name: 'Load historical data now' })).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: 'Add stock' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/stocks/SNOW'));
    expect(body).toEqual({ symbol: 'SNOW', mic: 'XNYS', loadHistory: true });
    expect(await screen.findByText('Added SNOW (Snowflake Inc.). Historical data load queued. Job #aa11bb22')).toBeInTheDocument();
    // The notification becomes reachable once the dialog has closed.
    await user.click(await screen.findByRole('button', { name: 'View job' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/app/admin/sync-jobs/${SNOW_JOB_ID}`));
  });

  it('shows provider results that cannot be added', async () => {
    signInAsAdmin();
    const { user } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'msf');

    const listed = await within(dialog).findByRole('radio', { name: /MSFT · Microsoft Corporation/ });
    expect(listed).toBeDisabled();
    expect(within(dialog).getByText('Already listed')).toBeInTheDocument();
    expect(within(dialog).getByRole('radio', { name: /MSFU/ })).toBeDisabled();
    expect(within(dialog).getByText('Unsupported instrument type')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Add stock' })).toBeDisabled();
  });

  it('falls back to manual entry when the provider cannot search', async () => {
    signInAsAdmin();
    let body: CreateStockRequest | undefined;
    const volvo = adminStock('VOLV-B', { companyName: 'Volvo B', exchange: 'OMX', mic: 'XSTO', currency: 'SEK', country: 'SE', source: 'MANUAL' });
    server.use(
      http.get(api('/admin/stocks/provider-search'), () => HttpResponse.json({
        provider: { id: 'synthetic', name: 'Synthetic demo data' }, supported: false, results: [],
      })),
      http.post(api('/admin/stocks'), async ({ request }) => {
        body = (await request.json()) as CreateStockRequest;
        return HttpResponse.json({ stock: volvo, job: null }, { status: 201 });
      }),
      http.get(api('/admin/stocks/VOLV-B'), () => HttpResponse.json(volvo)),
    );
    const { user, router } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'volv-b');
    const notice = await within(dialog).findByText(/Synthetic demo data cannot search for instruments/);
    await user.click(within(notice.closest('[role="alert"]') as HTMLElement).getByRole('button', { name: 'Enter details manually' }));

    const manual = await screen.findByRole('dialog', { name: 'Add stock manually' });
    expect(within(manual).getByLabelText('Symbol')).toHaveValue('VOLV-B');
    await user.type(within(manual).getByLabelText('Company name'), 'Volvo B');
    await user.type(within(manual).getByLabelText('Exchange'), 'OMX');
    await user.type(within(manual).getByLabelText('MIC'), 'xs');
    await user.type(within(manual).getByLabelText('Currency'), 'sek');
    await user.type(within(manual).getByLabelText('Country'), 'se');
    await user.click(within(manual).getByRole('button', { name: 'Add stock' }));
    expect(await within(manual).findByText('Enter the 4-character market identifier code, e.g. XNAS')).toBeInTheDocument();

    await user.type(within(manual).getByLabelText('MIC'), 'to');
    await user.click(within(manual).getByRole('button', { name: 'Add stock' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/app/admin/stocks/VOLV-B'));
    expect(body).toEqual({
      symbol: 'VOLV-B',
      loadHistory: true,
      manual: {
        companyName: 'Volvo B', exchange: 'OMX', mic: 'XSTO', currency: 'SEK', country: 'SE', sector: null, industry: null,
        assetType: 'COMMON_STOCK',
      },
    });
    expect(await screen.findByText('Added VOLV-B (Volvo B)')).toBeInTheDocument();
  });

  it('suggests manual entry when the provider has no metadata for the instrument', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/stocks'), () => apiError(422, 'PROVIDER_METADATA_UNAVAILABLE',
      'The provider returned no metadata for SNOW')));
    const { user } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'snow');
    await user.click(await within(dialog).findByRole('radio', { name: /SNOW · Snowflake Inc\./ }));
    await user.click(within(dialog).getByRole('button', { name: 'Add stock' }));

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('The provider returned no metadata for SNOW You can enter the details manually instead.');
    await user.click(within(alert).getByRole('button', { name: 'Enter details manually' }));

    const manual = await screen.findByRole('dialog', { name: 'Add stock manually' });
    expect(within(manual).getByLabelText('Symbol')).toHaveValue('SNOW');
    expect(within(manual).getByLabelText('Company name')).toHaveValue('Snowflake Inc.');
    expect(within(manual).getByLabelText('MIC')).toHaveValue('XNYS');
    expect(within(manual).getByLabelText('Currency')).toHaveValue('USD');
  });

  it('explains a rate-limited provider search', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/stocks/provider-search'),
      () => apiError(429, 'MARKET_DATA_PROVIDER_RATE_LIMITED', 'Too many requests')));
    const { user } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'snow');

    expect(await within(dialog).findByText('Provider rate limit reached. Try again in a minute.')).toBeInTheDocument();
  });

  it('reports a stock that is already in the catalogue', async () => {
    signInAsAdmin();
    server.use(http.post(api('/admin/stocks'),
      () => apiError(409, 'STOCK_ALREADY_EXISTS', "A stock with symbol 'SNOW' already exists")));
    const { user } = renderApp('/app/admin/stocks');

    const dialog = await openAddStock(user);
    await user.type(within(dialog).getByLabelText('Search the provider by symbol or company'), 'snow');
    await user.click(await within(dialog).findByRole('radio', { name: /SNOW · Snowflake Inc\./ }));
    await user.click(within(dialog).getByRole('button', { name: 'Add stock' }));

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent("A stock with symbol 'SNOW' already exists");
    expect(within(alert).getByRole('button', { name: 'Open stock' })).toBeInTheDocument();
  });
});
