import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { providerHealth, providerOverview } from '@/test/adminFixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';
import { signInAsAdmin } from '@/test/signInAsAdmin';

const valueOf = (region: HTMLElement, label: string) => within(region).getByText(label).nextElementSibling?.textContent;

describe('AdminProvidersPage', () => {
  it('shows the active provider, its health, usage and capabilities but never an API key', async () => {
    signInAsAdmin();
    // Even if a key leaked into the response by mistake, the page only shows whether one is configured.
    server.use(http.get(api('/admin/market-data/providers'), () => HttpResponse.json({
      ...providerOverview(), active: { ...providerOverview().active, apiKey: 'td-live-0123456789abcdef' },
    })));
    renderApp('/app/admin/providers');

    const active = await screen.findByRole('region', { name: 'Active provider' });
    expect(within(active).getByText('Twelve Data')).toBeInTheDocument();
    expect(within(active).getByText('Healthy')).toBeInTheDocument();
    expect(within(active).getByText('Live market data')).toBeInTheDocument();
    expect(within(active).getByText('API key: configured')).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('td-live-0123456789abcdef');

    const health = screen.getByRole('region', { name: 'Health' });
    expect(within(health).getByText('Provider reachable; 3 of 8 requests used this minute')).toBeInTheDocument();
    expect(valueOf(health, 'Latency')).toBe('231 ms');
    expect(valueOf(health, 'Plan category')).toBe('basic');
    expect(valueOf(health, 'Daily limit')).toBe('800');

    const usage = screen.getByRole('region', { name: 'Usage today' });
    expect(valueOf(usage, 'Requests today')).toBe('120');
    expect(valueOf(usage, 'Rate-limited today')).toBe('1');
    expect(usage).toHaveTextContent('Market-data provider timed out');

    expect(screen.getByRole('region', { name: 'Rate limit' })).toHaveTextContent('5 of 8 requests available this minute');
    const capabilities = screen.getByRole('region', { name: 'Capabilities' });
    expect(valueOf(capabilities, 'Intervals')).toBe('Daily');
    expect(valueOf(capabilities, 'Symbol search')).toBe('Yes');

    const available = screen.getByRole('list', { name: 'Available providers' });
    expect(within(available).getAllByRole('listitem')).toHaveLength(2);
    expect(within(available).getByText('Active')).toBeInTheDocument();
    expect(screen.getByText(/Set MARKET_DATA_PROVIDER/)).toBeInTheDocument();
  });

  it('reports a missing API key', async () => {
    signInAsAdmin();
    server.use(
      http.get(api('/admin/market-data/providers'), () => HttpResponse.json(providerOverview({
        apiKeyConfigured: false, health: providerHealth({ status: 'MISCONFIGURED', message: 'TWELVE_DATA_API_KEY is not set' }),
      }))),
      http.get(api('/admin/market-data/providers/health'),
        () => HttpResponse.json(providerHealth({ status: 'MISCONFIGURED', message: 'TWELVE_DATA_API_KEY is not set' }))),
    );
    renderApp('/app/admin/providers');

    const active = await screen.findByRole('region', { name: 'Active provider' });
    expect(within(active).getByText('API key: missing')).toBeInTheDocument();
    expect(within(active).getByText('Misconfigured')).toBeInTheDocument();
  });

  it('explains that the synthetic demo provider needs no API key', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/market-data/providers'), () => HttpResponse.json(providerOverview({
      id: 'synthetic', name: 'Synthetic demo data', synthetic: true, apiKeyRequired: false, apiKeyConfigured: false,
    }))));
    renderApp('/app/admin/providers');

    const active = await screen.findByRole('region', { name: 'Active provider' });
    expect(within(active).getByText('API key: not required')).toBeInTheDocument();
    expect(within(active).queryByText('API key: missing')).not.toBeInTheDocument();
  });

  it('checks the provider health on demand', async () => {
    signInAsAdmin();
    let refreshed = false;
    server.use(
      http.get(api('/admin/market-data/providers/health'), ({ request }) => {
        if (new URL(request.url).searchParams.get('refresh') === 'true') refreshed = true;
        return HttpResponse.json(refreshed
          ? providerHealth({ status: 'DEGRADED', message: 'Slow responses from the provider', latencyMs: 2400 })
          : providerHealth());
      }),
      http.get(api('/admin/market-data/providers'), () => HttpResponse.json(providerOverview(refreshed
        ? { health: providerHealth({ status: 'DEGRADED', message: 'Slow responses from the provider', latencyMs: 2400 }) }
        : {}))),
    );
    const { user } = renderApp('/app/admin/providers');

    const health = await screen.findByRole('region', { name: 'Health' });
    expect(within(health).getByText('Healthy')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Check now' }));

    expect(await screen.findByText('Health check finished: Degraded. Slow responses from the provider')).toBeInTheDocument();
    expect(refreshed).toBe(true);
    await waitFor(() => expect(within(health).getByText('Degraded')).toBeInTheDocument());
    expect(valueOf(health, 'Latency')).toBe('2,400 ms');
    expect(within(screen.getByRole('region', { name: 'Active provider' })).getByText('Degraded')).toBeInTheDocument();
  });

  it('shows the backend message when the providers cannot be loaded', async () => {
    signInAsAdmin();
    server.use(http.get(api('/admin/market-data/providers'),
      () => apiError(503, 'MARKET_DATA_PROVIDER_UNAVAILABLE', 'Provider status unavailable')));
    renderApp('/app/admin/providers');

    expect(await screen.findByText('Unable to load the market-data providers')).toBeInTheDocument();
    expect(screen.getByText(/The market-data provider is unavailable right now/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
