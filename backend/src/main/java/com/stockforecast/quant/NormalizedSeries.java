package com.stockforecast.quant;

import java.time.LocalDate;
import java.util.List;

/**
 * A price series rebased to 100 at {@code baseDate}.
 *
 * @param partial the stock's history starts after the requested period start
 */
public record NormalizedSeries(LocalDate baseDate, boolean partial, List<Point> points) {

    public NormalizedSeries {
        points = List.copyOf(points);
    }

    /** Return over the series, e.g. 0.12 when the last point is 112. */
    public double totalReturn() {
        return points.isEmpty() ? 0 : points.getLast().value() / 100 - 1;
    }

    public record Point(LocalDate date, double value) {
    }
}
