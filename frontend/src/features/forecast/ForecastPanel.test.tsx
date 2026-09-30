import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { forecastResult } from '@/test/fixtures';
import { api, apiError } from '@/test/handlers';
import { renderApp } from '@/test/renderApp';
import { server } from '@/test/server';

/** Captures the query string of every forecast request. */
function recordForecastRequests() {
  const requests: URLSearchParams[] = [];
  server.use(http.get(api('/stocks/:symbol/forecast'), ({ request, params }) => {
    const query = new URL(request.url).searchParams;
    requests.push(query);
    return HttpResponse.json(forecastResult(String(params.symbol), {
      model: (query.get('model') ?? 'EXPONENTIAL_SMOOTHING') as never,
      target: (query.get('target') ?? 'PRICE') as never,
    }));
  }));
  return requests;
}

async function choose(user: ReturnType<typeof renderApp>['user'], field: string, option: RegExp | string) {
  await user.click(screen.getByRole('combobox', { name: field }));
  await user.click(await screen.findByRole('option', { name: option }));
}

describe('ForecastPanel', () => {
  it('shows the median estimate, range, chart, backtest performance, explanation and disclaimer', async () => {
    renderApp('/app/forecasts/MSFT');

    expect(await screen.findByRole('heading', { name: 'Microsoft Corporation forecast' })).toBeInTheDocument();
    expect(await screen.findByText('Median 5-day forecast')).toBeInTheDocument();
    expect(screen.getByText('$242.10')).toBeInTheDocument();
    expect(screen.getByText('$230.00 – $251.00')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: /MSFT price history and 5-trading-day Exponential smoothing forecast/ }))
      .toBeInTheDocument();

    const performance = screen.getByRole('region', { name: 'Historical backtest performance' });
    expect(within(performance).getByText('$4.21')).toBeInTheDocument();
    expect(within(performance).getByText('$5.83')).toBeInTheDocument();
    expect(within(performance).getByText('2.7%')).toBeInTheDocument();
    expect(within(performance).getByText('54.8%')).toBeInTheDocument();
    expect(within(performance).getByText('Similar to the naive baseline')).toBeInTheDocument();
    expect(within(performance).getByText(/50 forecasts, one every 5 trading days/)).toBeInTheDocument();

    const explanation = screen.getByRole('region', { name: 'About this model: Exponential smoothing' });
    expect(within(explanation).getByText(/Places more weight on recent observations/)).toBeInTheDocument();
    expect(within(explanation).getByText('Level smoothing (α)')).toBeInTheDocument();
    // Weights at their bounds must not be rounded to 1 or 0.
    expect(within(explanation).getByText('0.9999')).toBeInTheDocument();
    expect(within(explanation).getByText('0.0001')).toBeInTheDocument();
    expect(within(explanation).getByText('-3,776.6')).toBeInTheDocument();
    expect(within(explanation).getByText('504')).toBeInTheDocument();
    expect(await within(explanation).findByText(/Exponential smoothing limitations/)).toBeInTheDocument();
    expect(screen.getByText(/synthetic demo provider/)).toBeInTheDocument();
    expect(screen.getByText(/not financial advice/)).toBeInTheDocument();
  });

  it('sends the selected model, its setting, the horizon and the target', async () => {
    const requests = recordForecastRequests();
    const { user } = renderApp('/app/forecasts/MSFT');
    await screen.findByText('Median 5-day forecast');
    expect(requests[0].get('model')).toBe('EXPONENTIAL_SMOOTHING');
    expect(requests[0].get('variant')).toBe('AUTO');
    expect(requests[0].get('horizon')).toBe('30');

    await choose(user, 'Forecast model', /Linear regression trend/);
    await waitFor(() => expect(requests.at(-1)?.get('model')).toBe('LINEAR_REGRESSION'));
    expect(requests.at(-1)?.get('lookback')).toBe('252');
    expect(requests.at(-1)?.has('variant')).toBe(false);

    await choose(user, 'Trend lookback', '6 months (126 days)');
    await waitFor(() => expect(requests.at(-1)?.get('lookback')).toBe('126'));

    await user.click(screen.getByRole('button', { name: '10 trading days' }));
    await waitFor(() => expect(requests.at(-1)?.get('horizon')).toBe('10'));

    await user.click(screen.getByRole('button', { name: 'Return' }));
    await waitFor(() => expect(requests.at(-1)?.get('target')).toBe('RETURN'));

    await choose(user, 'Forecast model', /Moving average/);
    await choose(user, 'Moving-average window', 'SMA 50');
    await waitFor(() => expect(requests.at(-1)?.get('window')).toBe('50'));
    expect(requests.at(-1)?.get('model')).toBe('MOVING_AVERAGE');
    expect(requests.at(-1)?.has('lookback')).toBe(false);
  });

  it('lists planned models and automatic selection as unavailable', async () => {
    const { user } = renderApp('/app/forecasts/MSFT');
    await screen.findByText('Median 5-day forecast');

    await user.click(screen.getByRole('combobox', { name: 'Forecast model' }));

    expect(await screen.findByRole('option', { name: /Monte Carlo simulation/ })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('option', { name: /ARIMA/ })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('option', { name: /Auto/ })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByRole('option', { name: /Naive/ })).not.toHaveAttribute('aria-disabled');
  });

  it('shows a loading state until the forecast arrives', async () => {
    server.use(http.get(api('/stocks/:symbol/forecast'), async () => {
      await delay(300);
      return HttpResponse.json(forecastResult('MSFT'));
    }));
    renderApp('/app/forecasts/MSFT');

    expect(await screen.findByLabelText('Loading forecast')).toBeInTheDocument();
    expect(await screen.findByText('Median 5-day forecast')).toBeInTheDocument();
    expect(screen.queryByLabelText('Loading forecast')).not.toBeInTheDocument();
  });

  it('explains how much history is missing instead of forecasting', async () => {
    server.use(http.get(api('/stocks/:symbol/forecast'), () => apiError(422, 'INSUFFICIENT_FORECAST_HISTORY',
      'Not enough historical data is available: 250 trading days are required, 83 are available',
      { details: { requiredObservations: 250, availableObservations: 83, dataStatus: 'READY' } })));
    renderApp('/app/forecasts/MSFT');

    expect(await screen.findByText('Not enough historical data is available to generate this forecast.'))
      .toBeInTheDocument();
    expect(screen.getByText('Required: 250 trading days')).toBeInTheDocument();
    expect(screen.getByText('Available: 83 trading days')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /forecast/ })).not.toBeInTheDocument();
    expect(screen.getByText(/not financial advice/)).toBeInTheDocument();
  });

  it('asks for the stock to be watched when no history is collected', async () => {
    server.use(http.get(api('/stocks/:symbol/forecast'), () => apiError(422, 'INSUFFICIENT_FORECAST_HISTORY',
      'Not enough historical data', { details: { requiredObservations: 250, availableObservations: 0, dataStatus: 'NOT_TRACKED' } })));
    renderApp('/app/forecasts/KO');

    expect(await screen.findByText(/collected once it is on a watchlist/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add KO to a watchlist' })).toBeInTheDocument();
  });

  it('reports other failures with a retry', async () => {
    let calls = 0;
    server.use(http.get(api('/stocks/:symbol/forecast'), () => {
      calls += 1;
      return calls === 1 ? apiError(400, 'INVALID_FORECAST_PARAMETER', "Parameter 'window' must be between 5 and 200")
        : HttpResponse.json(forecastResult('MSFT'));
    }));
    const { user } = renderApp('/app/forecasts/MSFT');

    expect(await screen.findByText('Unable to generate the forecast')).toBeInTheDocument();
    expect(screen.getByText("Parameter 'window' must be between 5 and 200")).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Median 5-day forecast')).toBeInTheDocument();
  });

  it('explains a missing backtest without hiding the forecast', async () => {
    const reason = 'Not enough history to backtest this model at a 90-day horizon (435 trading days required, 300 available), so its historical accuracy is unknown.';
    server.use(http.get(api('/stocks/:symbol/forecast'), () => HttpResponse.json(forecastResult('MSFT', {
      backtest: null, warnings: [reason],
    }))));
    renderApp('/app/forecasts/MSFT');

    const performance = await screen.findByRole('region', { name: 'Historical backtest performance' });
    expect(within(performance).getByText(reason)).toBeInTheDocument();
    expect(screen.queryByText('Caveats')).not.toBeInTheDocument();
  });
});
