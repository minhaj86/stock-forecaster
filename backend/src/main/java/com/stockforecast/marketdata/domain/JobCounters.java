package com.stockforecast.marketdata.domain;

/**
 * Record counts of one job.
 *
 * @param fetched  bars received from the provider
 * @param inserted new dates stored
 * @param updated  stored bars whose values changed
 * @param skipped  bars not written (unchanged, or kept because existing data is skipped)
 * @param failed   bars rejected by validation
 * @param deleted  stored bars removed by a repopulation because the provider no longer reports them
 */
public record JobCounters(int fetched, int inserted, int updated, int skipped, int failed, int deleted) {

    public static final JobCounters NONE = new JobCounters(0, 0, 0, 0, 0, 0);

    public JobCounters {
        if (fetched < 0 || inserted < 0 || updated < 0 || skipped < 0 || failed < 0 || deleted < 0) {
            throw new IllegalArgumentException("counters must not be negative");
        }
    }

    public boolean changedData() {
        return inserted > 0 || updated > 0 || deleted > 0;
    }
}
