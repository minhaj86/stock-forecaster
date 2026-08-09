package com.stockforecast.forecasting.domain;

import com.stockforecast.forecasting.domain.feature.Feature;

import java.util.Set;

/**
 * Extension point for learned models (gradient boosting, random forests, recurrent or transformer networks). They
 * consume trailing-only features from {@link com.stockforecast.forecasting.domain.feature.FeatureEngineer}, and are
 * backtested walk-forward like every other model: training, feature scaling and hyper-parameter tuning must use only
 * the {@link ForecastInput} passed to {@link #forecast}.
 *
 * <p>Learned models are expected to run in a separate engine (see the {@code ForecastProvider} port), so the Java
 * code base has no implementation of this interface yet.
 */
public interface MachineLearningForecastModel extends StockForecastModel {

    /** Features the model is trained on. */
    Set<Feature> features();
}
