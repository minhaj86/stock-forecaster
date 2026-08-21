package com.stockforecast.forecasting.infrastructure;

import com.stockforecast.forecasting.application.ForecastProperties;
import com.stockforecast.forecasting.application.ForecastProvider;
import com.stockforecast.forecasting.domain.StockForecastModel;
import com.stockforecast.forecasting.domain.TradingCalendar;
import com.stockforecast.forecasting.domain.WeekdayTradingCalendar;
import com.stockforecast.forecasting.domain.backtest.ForecastBacktester;
import com.stockforecast.forecasting.domain.backtest.WalkForwardBacktester;
import com.stockforecast.forecasting.domain.feature.FeatureEngineer;
import com.stockforecast.forecasting.domain.model.EmpiricalIntervalCalibrator;
import com.stockforecast.forecasting.domain.model.ExponentialSmoothingForecastModel;
import com.stockforecast.forecasting.domain.model.LinearRegressionForecastModel;
import com.stockforecast.forecasting.domain.model.MovingAverageForecastModel;
import com.stockforecast.forecasting.domain.model.NaiveForecastModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the framework-free forecasting domain: models are plain classes configured from {@link ForecastProperties}.
 * A new model is added by declaring one more {@link StockForecastModel} bean; it is then served, backtested and listed
 * without changes to the API.
 */
@Configuration(proxyBeanMethods = false)
class ForecastingConfiguration {

    @Bean
    EmpiricalIntervalCalibrator intervalCalibrator(ForecastProperties properties) {
        return new EmpiricalIntervalCalibrator(properties.intervalCalibrationOrigins(),
                properties.intervalMinimumSamples());
    }

    @Bean
    NaiveForecastModel naiveForecastModel(EmpiricalIntervalCalibrator calibrator) {
        return new NaiveForecastModel(calibrator);
    }

    @Bean
    MovingAverageForecastModel movingAverageForecastModel(EmpiricalIntervalCalibrator calibrator,
                                                          ForecastProperties properties) {
        return new MovingAverageForecastModel(calibrator, properties.movingAverage().defaultWindow());
    }

    @Bean
    LinearRegressionForecastModel linearRegressionForecastModel(EmpiricalIntervalCalibrator calibrator,
                                                                ForecastProperties properties) {
        return new LinearRegressionForecastModel(calibrator, properties.linearRegression().defaultLookback());
    }

    @Bean
    ExponentialSmoothingForecastModel exponentialSmoothingForecastModel(EmpiricalIntervalCalibrator calibrator,
                                                                        ForecastProperties properties) {
        ForecastProperties.ExponentialSmoothing settings = properties.exponentialSmoothing();
        return new ExponentialSmoothingForecastModel(calibrator, new ExponentialSmoothingForecastModel.Settings(
                settings.defaultLookback(), settings.optimizeParameters(), settings.alpha(), settings.beta(),
                settings.phi()));
    }

    @Bean
    ForecastBacktester forecastBacktester() {
        return new WalkForwardBacktester();
    }

    @Bean
    ForecastProvider javaForecastProvider(List<StockForecastModel> models, ForecastBacktester backtester) {
        return new JavaForecastProvider(models, backtester);
    }

    @Bean
    TradingCalendar tradingCalendar() {
        return new WeekdayTradingCalendar();
    }

    @Bean
    FeatureEngineer featureEngineer() {
        return new FeatureEngineer();
    }
}
