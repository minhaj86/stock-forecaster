/** Mirrors the backend forecasting contract (`/api/v1/stocks/{symbol}/forecast`). */
export type ForecastModelType =
  | 'NAIVE' | 'MOVING_AVERAGE' | 'LINEAR_REGRESSION' | 'EXPONENTIAL_SMOOTHING' | 'MONTE_CARLO' | 'ARIMA';

export type ForecastTarget = 'PRICE' | 'RETURN' | 'DIRECTION' | 'VOLATILITY';

export type SmoothingVariant = 'AUTO' | 'SIMPLE' | 'HOLT' | 'DAMPED';

export type BaselineComparison = 'BETTER' | 'SIMILAR' | 'WORSE' | 'NOT_APPLICABLE';

export type IntervalMethod = 'EMPIRICAL' | 'EMPIRICAL_EXTRAPOLATED' | 'VOLATILITY_SCALED';

export interface FittedParameter {
  key: string;
  label: string;
  value: number;
  format: 'NUMBER' | 'PERCENT' | 'PRICE';
}

export interface SeriesPoint {
  date: string;
  value: number;
}

/** Values are prices for PRICE and decimal fractions (0.052 = 5.2%) for RETURN. */
export interface ForecastPoint {
  step: number;
  date: string;
  expected: number;
  lowerBound: number;
  upperBound: number;
}

export interface ForecastSummary {
  targetDate: string;
  lastClose: number;
  expectedPrice: number;
  lowerPrice: number;
  upperPrice: number;
  expectedReturn: number;
  lowerReturn: number;
  upperReturn: number;
}

export interface BacktestMetrics {
  mae: number;
  rmse: number;
  meanError: number;
  mape: number | null;
  smape: number | null;
  directionAccuracy: number | null;
  directionalCalls: number;
  upMoveShare: number | null;
  intervalCoverage: number | null;
  naiveRmse: number;
  skillVsNaive: number | null;
  baselineComparison: BaselineComparison;
}

export interface BacktestSummary {
  trainingStart: string;
  evaluationStart: string;
  evaluationEnd: string;
  observations: number;
  originStepDays: number;
  minimumTrainingObservations: number;
  metrics: BacktestMetrics;
}

export interface IntervalCalibration {
  method: IntervalMethod;
  origins: number;
  measuredSteps: number;
}

export interface ForecastResult {
  symbol: string;
  companyName: string;
  currency: string;
  model: ForecastModelType;
  modelName: string;
  modelSummary: string;
  specification: string;
  target: ForecastTarget;
  horizonTradingDays: number;
  confidenceLevel: number;
  generatedAt: string;
  dataThrough: string;
  lastSyncedAt: string | null;
  dataProvider: string | null;
  syntheticData: boolean;
  engine: string;
  parameters: Record<string, string>;
  fitted: FittedParameter[];
  history: SeriesPoint[];
  forecast: ForecastPoint[];
  summary: ForecastSummary;
  interval: IntervalCalibration;
  /** Absent when there is not enough history to backtest; a warning explains why. */
  backtest: BacktestSummary | null;
  notes: string[];
  warnings: string[];
  disclaimer: string;
}

export interface ParameterSpec {
  name: string;
  label: string;
  kind: 'INTEGER' | 'DECIMAL' | 'CHOICE';
  defaultValue: string | null;
  minimum: number | null;
  maximum: number | null;
  options: string[];
  description: string;
}

export interface ForecastModelInfo {
  model: ForecastModelType;
  name: string;
  /** Planned models are listed but cannot be requested yet. */
  available: boolean;
  engine: string | null;
  summary: string;
  assumptions: string;
  limitations: string;
  targets: ForecastTarget[];
  parameters: ParameterSpec[];
  minimumObservations: number | null;
}

export interface ForecastModelCatalog {
  symbol: string;
  history: {
    dataStatus: string;
    dataThrough: string | null;
    availableObservations: number;
    requiredObservations: number;
  };
  defaults: {
    model: ForecastModelType;
    target: ForecastTarget;
    horizon: number;
    maxHorizon: number;
    horizons: number[];
    confidenceLevel: number;
  };
  models: ForecastModelInfo[];
  disclaimer: string;
}

/** What the user selected; only the setting relevant to the chosen model is sent. */
export interface ForecastSettings {
  model: ForecastModelType;
  target: ForecastTarget;
  horizon: number;
  window: number;
  lookback: number;
  variant: SmoothingVariant;
}

export const FORECAST_DISCLAIMER =
  'Forecasts are based on historical data and statistical models. They are informational only and are not financial advice.';
