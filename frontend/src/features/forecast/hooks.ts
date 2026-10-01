import { useQuery } from '@tanstack/react-query';
import { forecastApi } from '@/api/forecastApi';
import { toApiError } from '@/api/apiError';
import { queryKeys } from '@/api/queryKeys';
import type { ForecastSettings } from '@/models/forecast';

const POLL_WHILE_COLLECTING_MS = 3_000;
export const INSUFFICIENT_HISTORY = 'INSUFFICIENT_FORECAST_HISTORY';

/** History requirements reported with an INSUFFICIENT_FORECAST_HISTORY error. */
export interface HistoryShortfall {
  required: number;
  available: number;
  dataStatus: string | null;
}

export function historyShortfall(error: unknown): HistoryShortfall | null {
  if (!error) return null;
  const apiError = toApiError(error);
  if (apiError.code !== INSUFFICIENT_HISTORY) return null;
  const { requiredObservations, availableObservations, dataStatus } = apiError.details;
  return {
    required: typeof requiredObservations === 'number' ? requiredObservations : 0,
    available: typeof availableObservations === 'number' ? availableObservations : 0,
    dataStatus: typeof dataStatus === 'string' ? dataStatus : null,
  };
}

export function useForecast(symbol: string, settings: ForecastSettings) {
  return useQuery({
    queryKey: queryKeys.forecast(symbol, settings),
    queryFn: ({ signal }) => forecastApi.forecast(symbol, settings, signal),
    // Keep the previous chart while other settings load, but never show another stock's forecast.
    placeholderData: (previous) => (previous?.symbol === symbol ? previous : undefined),
    staleTime: 5 * 60_000,
    // A just-watched stock has no history yet; poll until collection completes.
    refetchInterval: (query) =>
      (historyShortfall(query.state.error)?.dataStatus === 'PENDING' ? POLL_WHILE_COLLECTING_MS : false),
  });
}

export function useForecastModels(symbol: string) {
  return useQuery({
    queryKey: queryKeys.forecastModels(symbol),
    queryFn: () => forecastApi.models(symbol),
    staleTime: 10 * 60_000,
  });
}
