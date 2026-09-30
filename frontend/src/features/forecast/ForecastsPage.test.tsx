import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';

describe('ForecastsPage', () => {
  it('offers the watched stocks and opens a stock forecast', async () => {
    const { user, router } = renderApp('/app/forecasts');

    expect(await screen.findByRole('heading', { name: 'Forecasts' })).toBeInTheDocument();
    await user.click(await screen.findByRole('link', { name: 'Forecast AAPL' }));

    expect(router.state.location.pathname).toBe('/app/forecasts/AAPL');
    expect(await screen.findByRole('heading', { name: 'Apple Inc. forecast' })).toBeInTheDocument();
    expect(await screen.findByText('Median 5-day forecast')).toBeInTheDocument();
  });

  it('reports an unknown symbol', async () => {
    renderApp('/app/forecasts/NOPE');

    expect(await screen.findByText('No stock with symbol “NOPE”')).toBeInTheDocument();
  });
});
