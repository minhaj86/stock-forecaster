import type { ForecastModelCatalog, ForecastResult, ForecastSettings } from '@/models/forecast';
import { httpClient } from './httpClient';

/** Query parameters for a forecast: the model-specific setting is sent only for the model that uses it. */
export function forecastParams(settings: ForecastSettings): Record<string, string | number> {
  const params: Record<string, string | number> = {
    model: settings.model, target: settings.target, horizon: settings.horizon,
  };
  if (settings.model === 'MOVING_AVERAGE') params.window = settings.window;
  if (settings.model === 'LINEAR_REGRESSION') params.lookback = settings.lookback;
  if (settings.model === 'EXPONENTIAL_SMOOTHING') params.variant = settings.variant;
  return params;
}

const path = (symbol: string) => `/stocks/${encodeURIComponent(symbol)}/forecast`;

export const forecastApi = {
  forecast: async (symbol: string, settings: ForecastSettings, signal?: AbortSignal): Promise<ForecastResult> =>
    (await httpClient.get<ForecastResult>(path(symbol), { params: forecastParams(settings), signal })).data,

  models: async (symbol: string): Promise<ForecastModelCatalog> =>
    (await httpClient.get<ForecastModelCatalog>(`${path(symbol)}/models`)).data,
};
