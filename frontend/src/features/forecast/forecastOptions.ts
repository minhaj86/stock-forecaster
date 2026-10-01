import type { ForecastModelType, ForecastSettings, SmoothingVariant } from '@/models/forecast';

export const DEFAULT_SETTINGS: ForecastSettings = {
  model: 'EXPONENTIAL_SMOOTHING', target: 'PRICE', horizon: 30, window: 20, lookback: 252, variant: 'AUTO',
};

export const HORIZONS = [5, 10, 20, 30, 60, 90];

/** Display order; names come from the backend catalogue when it has loaded. */
export const MODEL_ORDER: ForecastModelType[] = [
  'NAIVE', 'MOVING_AVERAGE', 'LINEAR_REGRESSION', 'EXPONENTIAL_SMOOTHING', 'MONTE_CARLO', 'ARIMA',
];

export const MODEL_NAMES: Record<ForecastModelType, string> = {
  NAIVE: 'Naive (last close)',
  MOVING_AVERAGE: 'Moving average',
  LINEAR_REGRESSION: 'Linear regression trend',
  EXPONENTIAL_SMOOTHING: 'Exponential smoothing',
  MONTE_CARLO: 'Monte Carlo simulation',
  ARIMA: 'ARIMA',
};

/** Models that exist in the roadmap but are not served yet. */
export const PLANNED_MODELS: ForecastModelType[] = ['MONTE_CARLO', 'ARIMA'];

export const WINDOW_OPTIONS = [20, 50, 100, 200];

export const LOOKBACK_OPTIONS: { value: number; label: string }[] = [
  { value: 63, label: '3 months (63 days)' },
  { value: 126, label: '6 months (126 days)' },
  { value: 252, label: '1 year (252 days)' },
  { value: 504, label: '2 years (504 days)' },
];

export const VARIANT_OPTIONS: { value: SmoothingVariant; label: string }[] = [
  { value: 'AUTO', label: 'Auto (lowest AICc)' },
  { value: 'SIMPLE', label: 'Simple (level only)' },
  { value: 'HOLT', label: 'Holt (linear trend)' },
  { value: 'DAMPED', label: 'Damped trend' },
];
