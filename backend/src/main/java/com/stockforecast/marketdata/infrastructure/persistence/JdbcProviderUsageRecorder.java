package com.stockforecast.marketdata.infrastructure.persistence;

import com.stockforecast.marketdata.MarketDataProviders.ProviderUsage;
import com.stockforecast.marketdata.MarketDataProviders.RateLimitStatus;
import com.stockforecast.marketdata.application.ProviderErrorCode;
import com.stockforecast.marketdata.application.ProviderUsageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Daily counters per provider, upserted atomically so every instance contributes to the same numbers. Statistics are
 * best effort and independent of the caller: each write runs in its own transaction (provider calls may happen inside a
 * read-only transaction), and a failing write is logged, never propagated.
 */
@Component
class JdbcProviderUsageRecorder implements ProviderUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(JdbcProviderUsageRecorder.class);
    private static final int MAX_MESSAGE_LENGTH = 300;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    JdbcProviderUsageRecorder(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                              Clock clock) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public void recordSuccess(String provider) {
        Instant now = clock.instant();
        upsert("""
                INSERT INTO market_data_provider_usage (provider, usage_date, requests, last_success_at)
                VALUES (:provider, :day, 1, :now)
                ON CONFLICT (provider, usage_date) DO UPDATE
                   SET requests = market_data_provider_usage.requests + 1, last_success_at = :now""",
                parameters(provider, now));
    }

    @Override
    public void recordFailure(String provider, ProviderErrorCode code, String message) {
        Instant now = clock.instant();
        String safeMessage = message == null ? code.jobErrorCode()
                : message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        int rateLimited = code == ProviderErrorCode.RATE_LIMITED ? 1 : 0;
        upsert("""
                INSERT INTO market_data_provider_usage (provider, usage_date, requests, failures, rate_limited,
                                                        last_failure_at, last_failure_message)
                VALUES (:provider, :day, 1, 1, :rateLimited, :now, :message)
                ON CONFLICT (provider, usage_date) DO UPDATE
                   SET requests = market_data_provider_usage.requests + 1,
                       failures = market_data_provider_usage.failures + 1,
                       rate_limited = market_data_provider_usage.rate_limited + :rateLimited,
                       last_failure_at = :now, last_failure_message = :message""",
                parameters(provider, now).addValue("rateLimited", rateLimited).addValue("message", safeMessage));
    }

    @Override
    public ProviderUsage today(String provider, RateLimitStatus rateLimit) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("provider", provider)
                .addValue("day", today);
        List<ProviderUsage> todays = jdbc.query("""
                        SELECT requests, failures, rate_limited FROM market_data_provider_usage
                         WHERE provider = :provider AND usage_date = :day""", params,
                (rs, row) -> new ProviderUsage(rs.getLong("requests"), rs.getLong("failures"),
                        rs.getLong("rate_limited"), null, null, null, rateLimit));
        // Last success/failure may be on an earlier day.
        List<Object[]> latest = jdbc.query("""
                        SELECT max(last_success_at) AS last_success,
                               (SELECT u.last_failure_at FROM market_data_provider_usage u
                                 WHERE u.provider = :provider AND u.last_failure_at IS NOT NULL
                                 ORDER BY u.last_failure_at DESC LIMIT 1) AS last_failure,
                               (SELECT u.last_failure_message FROM market_data_provider_usage u
                                 WHERE u.provider = :provider AND u.last_failure_at IS NOT NULL
                                 ORDER BY u.last_failure_at DESC LIMIT 1) AS last_failure_message
                          FROM market_data_provider_usage WHERE provider = :provider""", params,
                (rs, row) -> new Object[]{rs.getTimestamp("last_success"), rs.getTimestamp("last_failure"),
                        rs.getString("last_failure_message")});
        Object[] last = latest.isEmpty() ? new Object[3] : latest.getFirst();
        ProviderUsage counts = todays.isEmpty() ? new ProviderUsage(0, 0, 0, null, null, null, rateLimit)
                : todays.getFirst();
        return new ProviderUsage(counts.requestsToday(), counts.failuresToday(), counts.rateLimitedToday(),
                instant((Timestamp) last[0]), instant((Timestamp) last[1]), (String) last[2], rateLimit);
    }

    private MapSqlParameterSource parameters(String provider, Instant now) {
        return new MapSqlParameterSource()
                .addValue("provider", provider)
                .addValue("day", LocalDate.ofInstant(now, ZoneOffset.UTC))
                .addValue("now", Timestamp.from(now));
    }

    private void upsert(String sql, MapSqlParameterSource params) {
        try {
            ownTransaction.executeWithoutResult(status -> jdbc.update(sql, params));
        } catch (DataAccessException e) {
            log.warn("Could not record market-data provider usage: {}", e.getMessage());
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
