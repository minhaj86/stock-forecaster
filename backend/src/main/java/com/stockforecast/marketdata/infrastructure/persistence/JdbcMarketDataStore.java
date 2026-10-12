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
import java.util.UUID;

/**
 * Time series are written with plain JDBC: natural composite keys, batched idempotent upserts and no need for
 * entity state tracking.
 */
@Repository
class JdbcMarketDataStore implements MarketDataStore {

    private static final int BATCH_SIZE = 500;
    private static final int MAX_ERROR_LENGTH = 500;

    private static final String INSERT_BAR = """
            INSERT INTO market_prices (stock_id, bar_date, open, high, low, close, adjusted_close, volume,
                                       data_provider, ingested_at)
            VALUES (:stockId, :date, :open, :high, :low, :close, :adjustedClose, :volume, :provider, :ingestedAt)
            """;

    private static final String UPSERT_BAR = INSERT_BAR + """
            ON CONFLICT (stock_id, bar_date) DO UPDATE
               SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                   adjusted_close = EXCLUDED.adjusted_close, volume = EXCLUDED.volume,
                   data_provider = EXCLUDED.data_provider, ingested_at = EXCLUDED.ingested_at""";

    private static final String INSERT_MISSING_BAR = INSERT_BAR + "ON CONFLICT (stock_id, bar_date) DO NOTHING";

    /** A staged bar whose values differ from the stored bar of the same date. */
    private static final String STAGED_DIFFERS = """
             (p.open <> s.open OR p.high <> s.high OR p.low <> s.low OR p.close <> s.close
              OR p.adjusted_close <> s.adjusted_close OR p.volume <> s.volume)""";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcMarketDataStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt) {
        writeBars(UPSERT_BAR, stockId, provider, bars, ingestedAt);
    }

    @Override
    public void insertMissingBars(UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt) {
        writeBars(INSERT_MISSING_BAR, stockId, provider, bars, ingestedAt);
    }

    private void writeBars(String sql, UUID stockId, String provider, List<DailyBar> bars, Instant ingestedAt) {
        Timestamp ingested = Timestamp.from(ingestedAt);
        for (int start = 0; start < bars.size(); start += BATCH_SIZE) {
            List<DailyBar> chunk = bars.subList(start, Math.min(start + BATCH_SIZE, bars.size()));
            SqlParameterSource[] batch = chunk.stream()
                    .map(bar -> barParameters(bar)
                            .addValue("stockId", stockId)
                            .addValue("provider", provider)
                            .addValue("ingestedAt", ingested))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate(sql, batch);
        }
    }

    private static MapSqlParameterSource barParameters(DailyBar bar) {
        return new MapSqlParameterSource()
                .addValue("date", bar.date())
                .addValue("open", bar.open())
                .addValue("high", bar.high())
                .addValue("low", bar.low())
                .addValue("close", bar.close())
                .addValue("adjustedClose", bar.adjustedClose())
                .addValue("volume", bar.volume());
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
    public long countBars(UUID stockId, LocalDate fromInclusive, LocalDate toInclusive) {
        Long count = jdbc.queryForObject("""
                        SELECT count(*) FROM market_prices
                         WHERE stock_id = :stockId AND bar_date BETWEEN :from AND :to""",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("from", fromInclusive)
                        .addValue("to", toInclusive), Long.class);
        return count == null ? 0 : count;
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
    @Transactional(readOnly = true)
    public Map<UUID, Coverage> findCoverage(Collection<UUID> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Coverage> result = new HashMap<>();
        jdbc.query("""
                        SELECT stock_id, min(bar_date) AS first_bar, max(bar_date) AS last_bar, count(*) AS bars
                          FROM market_prices
                         WHERE stock_id IN (:ids)
                         GROUP BY stock_id""",
                new MapSqlParameterSource("ids", stockIds),
                (ResultSet rs) -> {
                    result.put(rs.getObject("stock_id", UUID.class), new Coverage(
                            rs.getObject("first_bar", LocalDate.class), rs.getObject("last_bar", LocalDate.class),
                            rs.getLong("bars")));
                });
        return result;
    }

    @Override
    public void ensureTracked(UUID stockId, Instant now) {
        jdbc.update("""
                        INSERT INTO market_data_sync_status (stock_id, created_at) VALUES (:stockId, :now)
                        ON CONFLICT (stock_id) DO NOTHING""",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("now", Timestamp.from(now)));
    }

    @Override
    public void recordAttempt(UUID stockId, Instant now) {
        ensureTracked(stockId, now);
        jdbc.update("UPDATE market_data_sync_status SET last_attempt_at = :now WHERE stock_id = :stockId",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("now", Timestamp.from(now)));
    }

    @Override
    public void recordSuccess(UUID stockId, String provider, Instant now) {
        ensureTracked(stockId, now);
        jdbc.update("""
                        UPDATE market_data_sync_status
                           SET provider = :provider, last_success_at = :now, last_attempt_at = :now,
                               last_bar_date = (SELECT max(bar_date) FROM market_prices WHERE stock_id = :stockId),
                               consecutive_failures = 0, last_error = NULL
                         WHERE stock_id = :stockId""",
                new MapSqlParameterSource().addValue("stockId", stockId).addValue("provider", provider)
                        .addValue("now", Timestamp.from(now)));
    }

    @Override
    public void recordFailure(UUID stockId, String error, Instant now) {
        ensureTracked(stockId, now);
        String message = error == null ? "Unknown error" : error;
        jdbc.update("""
                        UPDATE market_data_sync_status
                           SET consecutive_failures = consecutive_failures + 1, last_error = :error,
                               last_attempt_at = :now
                         WHERE stock_id = :stockId""",
                new MapSqlParameterSource().addValue("stockId", stockId)
                        .addValue("error", message.length() > MAX_ERROR_LENGTH
                                ? message.substring(0, MAX_ERROR_LENGTH) : message)
                        .addValue("now", Timestamp.from(now)));
    }

    @Override
    @Transactional(readOnly = true)
    public TrackingStatistics trackingStatistics() {
        return jdbc.queryForObject("""
                        SELECT count(*) AS tracked, count(last_bar_date) AS with_data,
                               max(last_success_at) AS last_success
                          FROM market_data_sync_status""",
                new MapSqlParameterSource(),
                (rs, row) -> new TrackingStatistics(rs.getLong("tracked"), rs.getLong("with_data"),
                        instant(rs.getTimestamp("last_success"))));
    }

    @Override
    public void stageBars(UUID jobId, List<DailyBar> bars) {
        for (int start = 0; start < bars.size(); start += BATCH_SIZE) {
            List<DailyBar> chunk = bars.subList(start, Math.min(start + BATCH_SIZE, bars.size()));
            SqlParameterSource[] batch = chunk.stream()
                    .map(bar -> barParameters(bar).addValue("jobId", jobId))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    INSERT INTO market_price_staging (job_id, bar_date, open, high, low, close, adjusted_close, volume)
                    VALUES (:jobId, :date, :open, :high, :low, :close, :adjustedClose, :volume)""", batch);
        }
    }

    @Override
    public StagedSummary summarizeStaged(UUID jobId) {
        return jdbc.queryForObject("""
                        SELECT count(*) AS staged, min(bar_date) AS first_date, max(bar_date) AS last_date
                          FROM market_price_staging WHERE job_id = :jobId""",
                new MapSqlParameterSource("jobId", jobId),
                (rs, row) -> new StagedSummary(rs.getLong("staged"), rs.getObject("first_date", LocalDate.class),
                        rs.getObject("last_date", LocalDate.class)));
    }

    @Override
    public ReplaceResult replaceWithStaged(UUID stockId, UUID jobId, LocalDate fromInclusive, LocalDate toInclusive,
                                           String provider, Instant ingestedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("stockId", stockId).addValue("jobId", jobId)
                .addValue("from", fromInclusive).addValue("to", toInclusive)
                .addValue("provider", provider).addValue("ingestedAt", Timestamp.from(ingestedAt));
        // Serialise with any other writer of this stock's history for the rest of the transaction.
        ensureTracked(stockId, ingestedAt);
        jdbc.query("SELECT stock_id FROM market_data_sync_status WHERE stock_id = :stockId FOR UPDATE", params,
                (ResultSet rs) -> {
                });

        int inserted = count("""
                SELECT count(*) FROM market_price_staging s
                 WHERE s.job_id = :jobId AND s.bar_date BETWEEN :from AND :to
                   AND NOT EXISTS (SELECT 1 FROM market_prices p WHERE p.stock_id = :stockId AND p.bar_date = s.bar_date)
                """, params);
        int updated = count("""
                SELECT count(*) FROM market_price_staging s
                  JOIN market_prices p ON p.stock_id = :stockId AND p.bar_date = s.bar_date
                 WHERE s.job_id = :jobId AND s.bar_date BETWEEN :from AND :to AND""" + STAGED_DIFFERS, params);
        int staged = count("""
                SELECT count(*) FROM market_price_staging s
                 WHERE s.job_id = :jobId AND s.bar_date BETWEEN :from AND :to""", params);

        int deleted = jdbc.update("""
                DELETE FROM market_prices p
                 WHERE p.stock_id = :stockId AND p.bar_date BETWEEN :from AND :to
                   AND NOT EXISTS (SELECT 1 FROM market_price_staging s
                                    WHERE s.job_id = :jobId AND s.bar_date = p.bar_date)""", params);
        jdbc.update("""
                INSERT INTO market_prices (stock_id, bar_date, open, high, low, close, adjusted_close, volume,
                                           data_provider, ingested_at)
                SELECT :stockId, s.bar_date, s.open, s.high, s.low, s.close, s.adjusted_close, s.volume, :provider,
                       :ingestedAt
                  FROM market_price_staging s
                 WHERE s.job_id = :jobId AND s.bar_date BETWEEN :from AND :to
                ON CONFLICT (stock_id, bar_date) DO UPDATE
                   SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                       adjusted_close = EXCLUDED.adjusted_close, volume = EXCLUDED.volume,
                       data_provider = EXCLUDED.data_provider, ingested_at = EXCLUDED.ingested_at""", params);
        clearStaged(jobId);
        return new ReplaceResult(inserted, updated, staged - inserted - updated, deleted);
    }

    @Override
    public void clearStaged(UUID jobId) {
        jdbc.update("DELETE FROM market_price_staging WHERE job_id = :jobId", new MapSqlParameterSource("jobId", jobId));
    }

    private int count(String sql, MapSqlParameterSource params) {
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count == null ? 0 : count;
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
