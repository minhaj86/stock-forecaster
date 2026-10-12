package com.stockforecast.marketdata.application;

import com.stockforecast.marketdata.domain.JobCounters;

import java.util.Objects;

/**
 * A job cannot complete for a reason that is not a provider transport failure (invalid data, safety checks). Nothing
 * was written. The message is client-safe and stored on the job.
 */
public class JobExecutionException extends RuntimeException {

    private final String errorCode;
    private final transient JobCounters counters;

    public JobExecutionException(String errorCode, String message, JobCounters counters) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.counters = counters;
    }

    public String errorCode() {
        return errorCode;
    }

    public JobCounters counters() {
        return counters;
    }
}
