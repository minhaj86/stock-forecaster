/**
 * Quantitative library: pure, framework-free financial math shared by analytics and forecasting, so both use one
 * definition of every metric and neither module has to depend on the other.
 *
 * <p>Published API: {@link com.stockforecast.quant.PriceSeries} (point-in-time metrics),
 * {@link com.stockforecast.quant.NormalizedSeries} and {@link com.stockforecast.quant.Indicators} (trailing
 * indicator series that never look ahead).
 */
@ApplicationModule(displayName = "Quantitative Library")
package com.stockforecast.quant;

import org.springframework.modulith.ApplicationModule;
