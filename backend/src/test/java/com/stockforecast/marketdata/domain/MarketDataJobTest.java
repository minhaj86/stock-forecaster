package com.stockforecast.marketdata.domain;

import com.stockforecast.marketdata.ExistingDataStrategy;
import com.stockforecast.marketdata.MarketDataInterval;
import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataJobNotCancellableException;
import com.stockforecast.marketdata.domain.MarketDataExceptions.MarketDataJobNotRetryableException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataJobTest {

    private static final Instant NOW = Instant.parse("2026-10-11T22:00:00Z");
    private static final UUID STOCK = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2015, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 9);

    @Test
    void runsThroughItsLifecycle() {
        MarketDataJob job = backfill();
        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);

        job.start(NOW);
        job.reportProgress(150, "Fetching", NOW.plusSeconds(1));
        assertThat(job.getProgress()).isEqualTo(99);
        assertThat(job.getAttempts()).isEqualTo(1);

        job.complete(new JobCounters(2711, 1930, 0, 781, 0, 0), "Done", NOW.plusSeconds(21));

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.COMPLETED);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.durationMillis()).isEqualTo(21_000);
        assertThat(job.getCounters().skipped()).isEqualTo(781);
    }

    @Test
    void rejectedBarsMakeTheJobPartiallyCompleted() {
        MarketDataJob job = backfill();
        job.start(NOW);

        job.complete(new JobCounters(10, 7, 0, 0, 3, 0), "Done", NOW);

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.PARTIALLY_COMPLETED);
    }

    @Test
    void transientFailuresAreRetriedUntilTheAttemptsAreUsedUp() {
        MarketDataJob job = backfill();
        job.start(NOW);
        job.scheduleRetry("PROVIDER_TIMEOUT", "timed out", NOW.plusSeconds(60));

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(job.hasAttemptsLeft()).isTrue();

        job.start(NOW.plusSeconds(60));
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getErrorCode()).isNull();
        assertThat(job.hasAttemptsLeft()).isFalse();
    }

    @Test
    void waitingForTheRateLimitDoesNotUseAnAttempt() {
        MarketDataJob job = backfill();
        job.start(NOW);

        job.deferForRateLimit(NOW.plusSeconds(45), "rate limited");

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getErrorCode()).isEqualTo("PROVIDER_RATE_LIMITED");
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(45));
    }

    @Test
    void onlyQueuedJobsCanBeCancelled() {
        MarketDataJob queued = backfill();
        queued.cancel(NOW);
        assertThat(queued.getStatus()).isEqualTo(MarketDataJobStatus.CANCELLED);

        MarketDataJob running = backfill();
        running.start(NOW);
        assertThatThrownBy(() -> running.cancel(NOW)).isInstanceOf(MarketDataJobNotCancellableException.class);
    }

    @Test
    void onlyTransientFailuresCanBeRetriedAndTheRetryIsLinked() {
        MarketDataJob permanent = backfill();
        permanent.start(NOW);
        permanent.fail("SYMBOL_NOT_FOUND", "unknown symbol", false, null, NOW);
        assertThatThrownBy(() -> permanent.retry(ADMIN, 5, NOW))
                .isInstanceOf(MarketDataJobNotRetryableException.class);

        MarketDataJob transientFailure = backfill();
        transientFailure.start(NOW);
        transientFailure.fail("PROVIDER_UNAVAILABLE", "HTTP 503", true, null, NOW);
        MarketDataJob retry = transientFailure.retry(ADMIN, 5, NOW.plusSeconds(1));

        assertThat(retry.getRetryOfJobId()).isEqualTo(transientFailure.getId());
        assertThat(retry.getStatus()).isEqualTo(MarketDataJobStatus.QUEUED);
        assertThat(retry.getTrigger()).isEqualTo(MarketDataJobTrigger.ADMIN);
        assertThat(retry.getFromDate()).isEqualTo(FROM);
        assertThat(retry.getExistingDataStrategy()).isEqualTo(ExistingDataStrategy.SKIP);
    }

    @Test
    void abandonedJobsAreQueuedAgainOrFailWhenNoAttemptsRemain() {
        MarketDataJob job = MarketDataJob.queue(request(MarketDataJobType.BACKFILL, FROM, TO), "synthetic", 1, NOW);
        job.start(NOW);

        job.recoverAbandoned(NOW.plus(Duration.ofMinutes(20)), Duration.ofMinutes(1));

        assertThat(job.getStatus()).isEqualTo(MarketDataJobStatus.FAILED);
        assertThat(job.isRetryable()).isTrue();
    }

    @Test
    void recognisesDuplicateRequests() {
        MarketDataJob job = backfill();

        assertThat(job.isEquivalentActiveJob(request(MarketDataJobType.BACKFILL, FROM, TO))).isTrue();
        assertThat(job.isEquivalentActiveJob(request(MarketDataJobType.BACKFILL, FROM.plusDays(1), TO))).isFalse();
        job.cancel(NOW);
        assertThat(job.isEquivalentActiveJob(request(MarketDataJobType.BACKFILL, FROM, TO))).isFalse();
    }

    @Test
    void rejectsInvertedRanges() {
        assertThatThrownBy(() -> MarketDataJob.queue(request(MarketDataJobType.BACKFILL, TO, FROM), "synthetic", 5,
                NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketDataJob backfill() {
        return MarketDataJob.queue(request(MarketDataJobType.BACKFILL, FROM, TO), "synthetic", 2, NOW);
    }

    private static MarketDataJob.Request request(MarketDataJobType type, LocalDate from, LocalDate to) {
        return new MarketDataJob.Request(type, MarketDataJobTrigger.ADMIN, STOCK, MarketDataInterval.ONE_DAY,
                ExistingDataStrategy.SKIP, from, to, ADMIN, null);
    }
}
