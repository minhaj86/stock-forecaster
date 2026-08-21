package com.stockforecast.forecasting.infrastructure;

import com.stockforecast.forecasting.application.ForecastProvider;
import com.stockforecast.forecasting.domain.ForecastInput;
import com.stockforecast.forecasting.domain.ForecastModelType;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.backtest.BacktestRequest;
import com.stockforecast.forecasting.domain.backtest.BacktestResult;
import com.stockforecast.forecasting.domain.backtest.ForecastBacktester;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the in-process Java models; every {@link StockForecastModel} bean is registered by its type.
 */
class JavaForecastProvider implements ForecastProvider {

    private final Map<ForecastModelType, StockForecastModel> models = new EnumMap<>(ForecastModelType.class);
    private final ForecastBacktester backtester;

    JavaForecastProvider(List<StockForecastModel> models, ForecastBacktester backtester) {
        for (StockForecastModel model : models) {
            if (this.models.put(model.type(), model) != null) {
                throw new IllegalStateException("Duplicate forecasting model " + model.type());
            }
        }
        this.backtester = backtester;
    }

    @Override
    public String id() {
        return "java";
    }

    @Override
    public Set<ForecastModelType> supportedModels() {
        return Collections.unmodifiableSet(models.keySet());
    }

    @Override
    public StockForecastModel model(ForecastModelType type) {
        StockForecastModel model = models.get(type);
        if (model == null) {
            throw new IllegalArgumentException("Model not supported by the Java provider: " + type);
        }
        return model;
    }

    @Override
    public BacktestResult backtest(ForecastModelType type, ForecastInput input, BacktestRequest request) {
        return backtester.backtest(model(type), input, request);
    }
}
