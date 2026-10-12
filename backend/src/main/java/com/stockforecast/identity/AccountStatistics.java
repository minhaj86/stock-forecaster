package com.stockforecast.identity;

/**
 * Aggregate account counts for the admin console.
 */
public record AccountStatistics(long total, long active, long disabled, long locked, long administrators,
                                long registeredLast7Days, long registeredLast30Days) {
}
