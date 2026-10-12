package com.stockforecast.marketdata.application;

/**
 * Receives progress of a running job (percent 0-99 and a short, client-safe description of the current step).
 */
@FunctionalInterface
public interface JobProgress {

    JobProgress NONE = (percent, message) -> {
    };

    void report(int percent, String message);
}
