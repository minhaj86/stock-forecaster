---
name: forecast-model
description: How to add or change a forecasting model in StockLens (StockForecastModel SPI, calibrated uncertainty ranges, walk-forward backtesting, leakage and golden-value tests, API exposure and the forecast UI). Use when asked to implement Monte Carlo, ARIMA, machine-learning models, model comparison/selection, or to change forecasting parameters or metrics.
---

# Adding a forecasting model

Read `backend/AGENTS.md` (Forecasting) and `docs/architecture.md` §9 first. Templates:
`forecasting/domain/model/MovingAverageForecastModel.java` (simplest) and
`ExponentialSmoothingForecastModel.java` (parameters, optimisation, variant selection).

## Non-negotiables

- A forecast is an **estimate with a range**, never a prediction. Responses keep the disclaimer
  (`ForecastViews.DISCLAIMER`); UI copy says "median estimate" and "estimated range", never "will be".
- **No look-ahead.** The model may only use the `ForecastInput` it receives. No statistics, scaling or tuning over
  data the backtester did not pass in. Features must come from trailing calculations (`quant.Indicators`).
- Use a maintained library for numerics (Hipparchus 4 is on the classpath). If none is reliable in Java (ARIMA,
  GARCH, ML), implement a `ForecastProvider` that calls an external engine instead of writing it by hand.
- Do not register a placeholder: planned models stay unavailable (`UNSUPPORTED_FORECAST_MODEL`) until real.
- Too little history must raise `InsufficientForecastHistoryException`, never produce a weaker forecast.

## 1. Domain model

1. Add the type to `ForecastModelType` if it is new (it is then listed as planned automatically).
2. Create `forecasting/domain/model/<Name>ForecastModel.java`:
   - Price-path models extend `CalibratedPricePathModel` and implement `type()`, `parameters()` (each a
     `ParameterSpec` with bounds and description), `minimumObservations(...)` and `fit(closes, parameters)`, which
     returns a `PointForecaster` whose `forecastFrom(origin, steps)` uses **only** `closes[0..origin]`. The base class
     adds ranges calibrated on the model's own out-of-sample errors (`EmpiricalIntervalCalibrator`).
   - Distributional models (e.g. Monte Carlo) implement `StockForecastModel` directly and fill `PathPoint`
     `expected`/`lower`/`upper` from simulated percentiles (seeded `RandomGenerator` passed in, never global
     randomness). Extend `ModelForecast`/`PathPoint` with extra percentiles only if the UI needs them, and keep
     only aggregated percentiles, never simulated paths, in caches.
   - Report fitted values as `FittedParameter`s (`NUMBER`, `PERCENT` or `PRICE`) and caveats as notes.
3. Override `resolveParameters` for cross-parameter rules (see the exponential-smoothing variant/weight checks).

## 2. Wiring and configuration

- Declare a bean in `forecasting/infrastructure/ForecastingConfiguration.java`; `JavaForecastProvider` registers it
  by type, and the API, backtest and catalogue pick it up without changes.
- New settings go into `ForecastProperties` (with `@DefaultValue` and validation), `application.yml` under
  `app.forecast`, and `ForecastProperties.fingerprint()` when they change results (cache keys).
- Add the plain-language description (summary, assumptions, limitations) to `application/ModelDescriptions.java`.
- New request parameters: add them to `ForecastController.ModelSettingsParameters` (the model rejects ones it does
  not use).

## 3. Tests

Golden values must be computed independently. A scratch environment works well:

```bash
python3 -m venv /tmp/fcvenv && /tmp/fcvenv/bin/pip install -q numpy scipy statsmodels
/tmp/fcvenv/bin/python - <<'PY'
import numpy as np
t = np.arange(100); y = np.log(100 * np.exp(0.001 * t + 0.02 * np.sin(0.5 * t)))
print(repr(np.polyfit(np.arange(60), y[40:], 1)))      # paste printed values into the Java test
PY
```

- Unit test the model against the golden values (`within(1e-9)` or tighter for closed forms), parameter validation,
  insufficient history and range widening (`NaiveForecastModelTest`, `LinearRegressionForecastModelTest`).
- **Leakage proof:** add the model to `WalkForwardBacktesterTest.realModels()`; the parameterised test alters
  future prices and requires earlier forecasts to stay bit-identical.
- Integration: add a row to `ForecastApiIT.everyAvailableModelForecasts`.

## 4. Frontend

- `frontend/src/features/forecast/forecastOptions.ts`: `MODEL_ORDER`, `MODEL_NAMES`; remove it from
  `PLANNED_MODELS` once available.
- Model-specific controls in `ForecastControls.tsx`; send them only for that model in `forecastParams`
  (`src/api/forecastApi.ts`).
- Extend `ForecastPanel.test.tsx` (the request carries the new parameter) and `forecastChartOption.test.ts` if the
  chart changes (e.g. P10–P90 bands).

## 5. Verify and document

```bash
cd backend && ./mvnw test -Dtest='*ForecastModelTest,WalkForwardBacktesterTest'
./mvnw verify -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ForecastApiIT
cd ../frontend && npx vitest run src/features/forecast
```

Then run `.github/skills/verify-changes/scripts/verify.sh` and update the models table in `README.md`
(Forecasting) and `docs/architecture.md` §9.
