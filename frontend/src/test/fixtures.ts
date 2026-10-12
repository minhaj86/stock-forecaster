import type { Dashboard, StockSnapshot } from '@/models/analytics';
import type { AuthResponse } from '@/models/auth';
import type { ForecastModelCatalog, ForecastResult } from '@/models/forecast';
import type { Stock } from '@/models/stock';
import type { UserProfile } from '@/models/user';
import type { Watchlist, WatchlistSummary } from '@/models/watchlist';

export const USER_ID = '01a12b0d-6752-7003-8400-7ca030e3dccb';

export function session(overrides: Partial<AuthResponse> = {}): AuthResponse {
  return {
    accessToken: 'access-token-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    expiresAt: '2026-10-11T13:15:00Z',
    user: { id: USER_ID, email: 'ada@example.com', role: 'USER', passwordChangeRequired: false },
    ...overrides,
  };
}

export const profile: UserProfile = {
  id: USER_ID,
  email: 'ada@example.com',
  role: 'USER',
  displayName: 'Ada Lovelace',
  timeZone: 'Europe/Berlin',
  memberSince: '2026-01-15T10:00:00Z',
  updatedAt: '2026-10-01T08:00:00Z',
};

export const stocks: Record<string, Stock> = {
  AAPL: { symbol: 'AAPL', companyName: 'Apple Inc.', exchange: 'NASDAQ', mic: 'XNAS', currency: 'USD', country: 'US', sector: 'Information Technology', industry: 'Technology Hardware, Storage & Peripherals', assetType: 'COMMON_STOCK' },
  MSFT: { symbol: 'MSFT', companyName: 'Microsoft Corporation', exchange: 'NASDAQ', mic: 'XNAS', currency: 'USD', country: 'US', sector: 'Information Technology', industry: 'Software', assetType: 'COMMON_STOCK' },
  MSTR: { symbol: 'MSTR', companyName: 'Strategy Inc', exchange: 'NASDAQ', mic: 'XNAS', currency: 'USD', country: 'US', sector: 'Information Technology', industry: 'Software', assetType: 'COMMON_STOCK' },
  NVDA: { symbol: 'NVDA', companyName: 'NVIDIA Corporation', exchange: 'NASDAQ', mic: 'XNAS', currency: 'USD', country: 'US', sector: 'Information Technology', industry: 'Semiconductors & Semiconductor Equipment', assetType: 'COMMON_STOCK' },
  KO: { symbol: 'KO', companyName: 'The Coca-Cola Company', exchange: 'NYSE', mic: 'XNYS', currency: 'USD', country: 'US', sector: 'Consumer Staples', industry: 'Beverages', assetType: 'COMMON_STOCK' },
};

export function snapshot(symbol: string, overrides: Partial<StockSnapshot> = {}): StockSnapshot {
  const stock = stocks[symbol];
  return {
    symbol,
    companyName: stock?.companyName ?? `${symbol} Inc.`,
    exchange: stock?.exchange ?? 'NASDAQ',
    currency: 'USD',
    sector: stock?.sector ?? null,
    dataStatus: 'READY',
    dataProvider: 'synthetic',
    asOf: '2026-10-09',
    lastSyncedAt: '2026-10-11T13:42:00Z',
    syncIssue: null,
    price: 100,
    previousClose: 99,
    change: 1,
    changePercent: 0.0101,
    return1W: 0.01,
    return1M: 0.02,
    return3M: 0.03,
    return6M: 0.05,
    returnYtd: 0.08,
    return1Y: 0.12,
    volatility30d: 0.2,
    volatility90d: 0.22,
    maxDrawdown1Y: -0.1,
    rsi14: 55,
    momentum3M: 0.03,
    ...overrides,
  };
}

export const watchlistSummaries: WatchlistSummary[] = [
  { id: 'wl-main', name: 'My Watchlist', stockCount: 3, symbols: ['AAPL', 'MSFT', 'NVDA'], createdAt: '2026-01-15T10:00:00Z', updatedAt: '2026-10-01T08:00:00Z' },
  { id: 'wl-tech', name: 'Technology', stockCount: 1, symbols: ['MSFT'], createdAt: '2026-02-01T10:00:00Z', updatedAt: '2026-09-01T08:00:00Z' },
];

export function watchlist(id = 'wl-main', name = 'My Watchlist', symbols = ['AAPL', 'MSFT', 'NVDA']): Watchlist {
  return {
    id, name, stockCount: symbols.length, createdAt: '2026-01-15T10:00:00Z', updatedAt: '2026-10-01T08:00:00Z',
    items: symbols.map((symbol, index) => {
      const stock = stocks[symbol];
      return {
        stock: { symbol, companyName: stock.companyName, exchange: stock.exchange, currency: 'USD', sector: stock.sector, assetType: stock.assetType },
        addedAt: `2026-03-0${index + 1}T10:00:00Z`,
        notes: null,
      };
    }),
  };
}

export function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    generatedAt: '2026-10-11T13:45:00Z',
    period: '1M',
    watchlistCount: 2,
    stockCount: 3,
    truncated: false,
    dataAsOf: '2026-10-09',
    lastSyncedAt: '2026-10-11T13:42:00Z',
    dataProviders: ['synthetic'],
    stocks: [
      snapshot('AAPL', { price: 227.52, changePercent: 0.013, return1M: 0.052 }),
      snapshot('MSFT', { price: 512.3, changePercent: 0.008, return1M: 0.184, volatility30d: 0.18 }),
      snapshot('NVDA', { price: 182.1, changePercent: -0.005, return1M: -0.071, volatility30d: 0.327, momentum3M: 0.21 }),
    ],
    movers: {
      topPerformer: { symbol: 'MSFT', companyName: 'Microsoft Corporation', value: 0.184 },
      weakestPerformer: { symbol: 'NVDA', companyName: 'NVIDIA Corporation', value: -0.071 },
      highestVolatility: { symbol: 'NVDA', companyName: 'NVIDIA Corporation', value: 0.327 },
      lowestVolatility: { symbol: 'MSFT', companyName: 'Microsoft Corporation', value: 0.18 },
      strongestMomentum: { symbol: 'NVDA', companyName: 'NVIDIA Corporation', value: 0.21 },
    },
    performance: {
      period: '1M',
      asOf: '2026-10-09',
      series: ['AAPL', 'MSFT', 'NVDA'].map((symbol, index) => ({
        symbol, companyName: stocks[symbol].companyName, baseDate: '2026-09-09', partial: false,
        periodReturn: [0.052, 0.184, -0.071][index],
        points: [{ date: '2026-09-09', value: 100 }, { date: '2026-10-09', value: 100 * (1 + [0.052, 0.184, -0.071][index]) }],
      })),
    },
    ...overrides,
  };
}

const TRADING_DAYS = ['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09'];
const FORECAST_DAYS = ['2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16'];

/** A 5-day exponential smoothing price forecast with a backtest; tests override what they exercise. */
export function forecastResult(symbol = 'MSFT', overrides: Partial<ForecastResult> = {}): ForecastResult {
  return {
    symbol,
    companyName: stocks[symbol]?.companyName ?? symbol,
    currency: 'USD',
    model: 'EXPONENTIAL_SMOOTHING',
    modelName: 'Exponential smoothing',
    modelSummary: 'Places more weight on recent observations while estimating the underlying level and trend of the time series.',
    specification: 'Damped trend (α=0.981, β=0.012, φ=0.950), selected by AICc',
    target: 'PRICE',
    horizonTradingDays: 5,
    confidenceLevel: 0.8,
    generatedAt: '2026-10-11T15:00:00Z',
    dataThrough: '2026-10-09',
    lastSyncedAt: '2026-10-11T13:42:00Z',
    dataProvider: 'synthetic',
    syntheticData: true,
    engine: 'java',
    parameters: { lookback: '504', variant: 'AUTO' },
    fitted: [
      { key: 'alpha', label: 'Level smoothing (α)', value: 0.9999, format: 'NUMBER' },
      { key: 'beta', label: 'Trend smoothing (β)', value: 0.0001, format: 'NUMBER' },
      { key: 'trendPerDay', label: 'Current trend per day', value: 0.0004, format: 'PERCENT' },
      { key: 'aicc', label: 'AICc', value: -3776.64, format: 'NUMBER' },
      { key: 'observations', label: 'Observations fitted', value: 504, format: 'NUMBER' },
    ],
    history: TRADING_DAYS.map((date, index) => ({ date, value: 236 + index })),
    forecast: FORECAST_DAYS.map((date, index) => ({
      step: index + 1, date, expected: 240.5 + 0.4 * index, lowerBound: 234 - index, upperBound: 247 + index,
    })),
    summary: {
      targetDate: '2026-10-16', lastClose: 240, expectedPrice: 242.1, lowerPrice: 230, upperPrice: 251,
      expectedReturn: 0.00875, lowerReturn: -0.041667, upperReturn: 0.045833,
    },
    interval: { method: 'EMPIRICAL', origins: 250, measuredSteps: 5 },
    backtest: {
      trainingStart: '2021-10-11', evaluationStart: '2025-10-06', evaluationEnd: '2026-10-09', observations: 50,
      originStepDays: 5, minimumTrainingObservations: 1003,
      metrics: {
        mae: 4.21, rmse: 5.83, meanError: -0.3, mape: 0.027, smape: 0.0268, directionAccuracy: 0.548,
        directionalCalls: 42, upMoveShare: 0.52, intervalCoverage: 0.78, naiveRmse: 6.1, skillVsNaive: 0.0443,
        baselineComparison: 'SIMILAR',
      },
    },
    notes: ['Smoothing weights were estimated by minimising one-step-ahead squared errors on the fitted window.'],
    warnings: ['Prices come from the synthetic demo provider: they are simulated, so this forecast only illustrates the method.'],
    disclaimer: 'Forecasts are based on historical data and statistical models. They are informational only and are not financial advice.',
    ...overrides,
  };
}

export function forecastCatalog(symbol = 'MSFT'): ForecastModelCatalog {
  const model = (name: ForecastModelCatalog['models'][number]['model'], label: string, available = true) => ({
    model: name, name: label, available, engine: available ? 'java' : null,
    summary: `${label} summary`, assumptions: `${label} assumptions`, limitations: `${label} limitations`,
    targets: available ? ['PRICE', 'RETURN'] as const : [], parameters: [], minimumObservations: available ? 250 : null,
  });
  return {
    symbol,
    history: { dataStatus: 'READY', dataThrough: '2026-10-09', availableObservations: 1260, requiredObservations: 250 },
    defaults: { model: 'EXPONENTIAL_SMOOTHING', target: 'PRICE', horizon: 30, maxHorizon: 90, horizons: [5, 10, 20, 30, 60, 90], confidenceLevel: 0.8 },
    models: [
      model('NAIVE', 'Naive (last close)'), model('MOVING_AVERAGE', 'Moving average'),
      model('LINEAR_REGRESSION', 'Linear regression trend'), model('EXPONENTIAL_SMOOTHING', 'Exponential smoothing'),
      model('MONTE_CARLO', 'Monte Carlo simulation', false), model('ARIMA', 'ARIMA', false),
    ].map((info) => ({ ...info, targets: [...info.targets] })),
    disclaimer: 'Forecasts are based on historical data and statistical models. They are informational only and are not financial advice.',
  };
}

