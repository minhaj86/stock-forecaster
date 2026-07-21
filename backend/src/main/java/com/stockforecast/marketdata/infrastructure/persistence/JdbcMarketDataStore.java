package com.stockforecast.marketdata.infrastructure.persistence;

import com.stockforecast.marketdata.DailyBar;
import com.stockforecast.marketdata.MarketDataStatus;
import com.stockforecast.marketdata.MarketDataStatus.State;
import com.stockforecast.marketdata.application.MarketDataStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Time series are written with plain JDBC: natural composite keys, batched idempotent upserts and no need for
 * entity state tracking.
 */
@Repository
class JdbcMarketDataStore implements MarketDataStore {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_ERROR_LENGTH = 500;

    private static final String UPSERT_BAR = """
            INSERT INTO market_prices (stock_id, bar_date, open, high, low, close, adjusted_close, volume,
                                       data_provider, ingested_at)
            VALUES (:stockId, :date, :open, :high, :low, :close, :adjustedClose, :volume, :provider, :ingestedAt)
            ON CONFLICT (stock_id, bar_date) DO UPDATE
               SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                   adjusted_close = EXCLUDED.adjusted_close, volume = EXCLUDED.volume,
                   data_provider = EXCLUDED.data_provider, ingested_at = EXCLUDED.ingested_at""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcMarketDataStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt) {
        Timestamp ingested = Timestamp.from(ingestedAt);
        for (int start = 0; start < bars.size(); start += BATCH_SIZE) {
            List<DailyBar> chunk = bars.subList(start, Math.min(start + BATCH_SIZE, bars.size()));
            SqlParameterSource[] batch = chunk.stream()
                    .map(bar -> new MapSqlParameterSource()
                            .addValue("stockId", stockId)
                            .addValue("date", bar.date())
                            .addValue("open", bar.open())
                            .addValue("high", bar.high())
                            .addValue("low", bar.low())
                            .addValue("close", bar.close())
                            .addValue("adjustedClose", bar.adjustedClose())
                            .addValue("volume", bar.volume())
                            .addValue("provider", provider)
                            .addValue("ingestedAt", ingested))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate(UPSERT_BAR, batch);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<DailyBar>> findBars(Collection<UUID> stockIds, LocalDate fromInclusive,
                                              LocalDate toInclusive) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<DailyBar>> result = new HashMap<>();
        jdbc.query("""
                        SELECT stock_id, bar_date, open, high, low, close, adjusted_close, volume
                          FROM market_prices
                         WHERE stock_id IN (:ids) AND bar_date BETWEEN :from AND :to
                         ORDER BY stock_id, bar_date""",
                new MapSqlParameterSource().addValue("ids", stockIds).addValue("from", fromInclusive)
                        .addValue("to", toInclusive),
                (ResultSet rs) -> {
                    UUID stockId = rs.getObject("stock_id", UUID.class);
                    result.computeIfAbsent(stockId, id -> new ArrayList<>()).add(new DailyBar(
                            rs.getObject("bar_date", LocalDate.class), rs.getBigDecimal("open"),
                            rs.getBigDecimal("high"), rs.getBigDecimal("low"), rs.getBigDecimal("close"),
                            rs.getBigDecimal("adjusted_close"), rs.getLong("volume")));
                });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MarketDataStatus> findStatuses(Collection<UUID> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MarketDataStatus> result = new HashMap<>();
        jdbc.query("""
                        SELECT stock_id, provider, last_bar_date, last_success_at, last_attempt_at,
                               consecutive_failures, last_error
                          FROM market_data_sync_status
                         WHERE stock_id IN (:ids)""",
                new MapSqlParameterSource("ids", stockIds),
                (ResultSet rs) -> {
                    MarketDataStatus status = toStatus(rs);
                    result.put(status.stockId(), status);
                });
        return result;
    }

    @Override
    @Transactional
    public Optional<SyncClaim> tryClaim(UUID stockId, Instant now, Instant staleLeaseBefore, boolean ignoreBackoff) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("stockId", stockId)
                .addValue("now", Timestamp.from(now))
                .addValue("stale", Timestamp.from(staleLeaseBefore))
                .addValue("ignoreBackoff", ignoreBackoff);
        ensureTracked(stockId, now);
        List<SyncClaim> claimed = jdbc.query("""
                        UPDATE market_data_sync_status
                           SET sync_started_at = :now, last_attempt_at = :now
                         WHERE stock_id = :stockId
                           AND (sync_started_at IS NULL OR sync_started_at < :stale)
                           AND (:ignoreBackoff OR next_attempt_at IS NULL OR next_attempt_at <= :now)
                        RETURNING stock_id, last_bar_date, consecutive_failures""",
                params,
                (rs, row) -> new SyncClaim(rs.getObject("stock_id", UUID.class),
                        rs.getObject("last_bar_date", LocalDate.class), rs.getInt("consecutive_failures")));
        return claimed.stream().findFirst();
    }

    @Override
    public void ensureTracked(UUID stockId, Instant now) {
        jdbc.update("""
                        INSERT INTO market_data_sync_status (stock_id, created_at) VALUES (:stockId, :now)
                        ON CONFLICT (stock_id) DO NOTHING""",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("now", Timestamp.from(now)));
    }

    @Override
    public void recordSuccess(UUID stockId, String provider, LocalDate lastBarDate, Instant now) {
        jdbc.update("""
                        UPDATE market_data_sync_status
                           SET sync_started_at = NULL, provider = :provider, last_success_at = :now,
                               last_bar_date = :lastBarDate, consecutive_failures = 0, last_error = NULL,
                               next_attempt_at = NULL
                         WHERE stock_id = :stockId""",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("provider", provider)
                        .addValue("now", Timestamp.from(now)).addValue("lastBarDate", lastBarDate));
    }

    @Override
    @Transactional
    public void recordFailure(UUID stockId, String error, Instant now, Instant nextAttemptAt) {
        String message = error == null ? "Unknown error" : error;
        jdbc.update("""
                        UPDATE market_data_sync_status
                           SET sync_started_at = NULL, consecutive_failures = consecutive_failures + 1,
                               last_error = :error, next_attempt_at = :next
                         WHERE stock_id = :stockId""",
                new MapSqlParameterSource().addValue("stockId", stockId)
                        .addValue("error", message.length() > MAX_ERROR_LENGTH
                                ? message.substring(0, MAX_ERROR_LENGTH) : message)
                        .addValue("next", Timestamp.from(nextAttemptAt)));
    }

    private static MarketDataStatus toStatus(ResultSet rs) throws SQLException {
        UUID stockId = rs.getObject("stock_id", UUID.class);
        LocalDate lastBarDate = rs.getObject("last_bar_date", LocalDate.class);
        int failures = rs.getInt("consecutive_failures");
        State state = lastBarDate != null ? State.READY : failures > 0 ? State.FAILED : State.PENDING;
        return new MarketDataStatus(stockId, state, rs.getString("provider"), lastBarDate,
                instant(rs.getTimestamp("last_success_at")), instant(rs.getTimestamp("last_attempt_at")),
                rs.getString("last_error"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
