---
name: analytics-metric
description: How to add or change a financial metric (returns, volatility, drawdown, RSI, momentum, moving averages, etc.) in the StockLens analytics module with independently verified golden-value tests, cache safety and end-to-end display. Use when asked to add, fix or change any calculated market statistic.
---

# Adding an analytics metric

Financial numbers must be **correct, explainable and computed once** (backend), never in the UI or by an LLM.

## 1. Define the metric precisely

Write the definition before coding: inputs (adjusted closes, trading days vs calendar days), window, base value,
annualisation, edge cases (too little history → `OptionalDouble.empty()`), and output unit (decimal fraction, index
level, 0–100 score). Add it to the class Javadoc of `backend/src/main/java/com/stockforecast/quant/PriceSeries.java` (point-in-time
value) or `quant/Indicators.java` (trailing series, also used by forecasting features)
and to the metrics table in `docs/architecture.md` §8.

Existing conventions:

- Returns `P(last) / P(base) - 1`, base = last close **on or before** the start date (`MarketPeriod.startDate`).
- Volatility: sample standard deviation (n − 1) of daily **log** returns × √252.
- Maximum drawdown: `min(P(t) / runningPeak - 1)` (≤ 0).
- RSI: Wilder smoothing seeded with simple averages; 100 when there are no losses, 50 when flat.
- Momentum: rate of change over 63 trading days.

## 2. Implement in the domain

Add a method to `PriceSeries` (pure Java, no Spring) returning `OptionalDouble`. Never return `0` for "unknown".
If the metric is also useful as a series (indicators, model features), implement it once in `Indicators` (trailing:
index `t` uses observations `0..t` only, NaN during warm-up) and let `PriceSeries` return its last value.

## 3. Golden-value tests, computed independently

Compute the expected numbers outside the code under test, e.g.:

```bash
python3 - <<'PY'
import math, statistics
c = [100, 102, 101, 105, 103, 108]
lr = [math.log(c[i] / c[i - 1]) for i in range(1, len(c))]
print(repr(statistics.stdev(lr) * math.sqrt(252)))   # paste the printed value into the test
PY
```

Then assert with a tight tolerance (`within(1e-12)` for closed-form math) in `PriceSeriesTest` / `IndicatorsTest`
(the latter also proves indicators never look ahead). Also test edge
cases: too little history → empty, constant prices, monotonic series, and boundary dates (weekends or holidays
before the start date).

## 4. Expose it

1. Add the field to `analytics/application/StockSnapshot` (nullable `Double`) and compute it in
   `StockAnalyticsService.compute(...)`, rounding with `ratio(...)` (6 decimals) or `scaled(..., 2)` for scores.
2. **Cache safety**: snapshots are cached as JSON in Redis. A changed record shape must not read stale entries:
   bump `spring.cache.redis.key-prefix` in `backend/src/main/resources/application.yml` (`sf:v1:` → `sf:v2:`).
3. If the lookback grows (e.g. SMA200 needs 200+ trading days), check `SNAPSHOT_LOOKBACK_DAYS` in
   `StockAnalyticsService`.
4. Integration test: extend `analytics/api/DashboardApiIT` (or a new `*IT`) to assert the field is present and
   plausible for a synthetic stock (exact values are covered by the unit tests).

## 5. Frontend

1. Add the field to `frontend/src/models/analytics.ts` (`number | null`).
2. Display it with the right formatter (`formatPercent(value, { signed: false })` for levels such as volatility,
   `<ChangeIndicator>` for signed changes, `formatNumber` for scores) and a tooltip explaining the definition.
3. Update fixtures in `src/test/fixtures.ts` and assert the rendered value in a page test.

## 6. Verify

`./mvnw verify` and `npm test`. Never adjust a golden value to match the implementation without re-deriving it
independently.
