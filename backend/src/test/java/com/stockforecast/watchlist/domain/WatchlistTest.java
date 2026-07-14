package com.stockforecast.watchlist.domain;

import com.stockforecast.watchlist.domain.WatchlistExceptions.InvalidWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.StockAlreadyInWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.StockNotInWatchlistException;
import com.stockforecast.watchlist.domain.WatchlistExceptions.WatchlistFullException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-11T12:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void createsAWatchlistWithATrimmedName() {
        Watchlist watchlist = Watchlist.create(OWNER, "  Tech  ", CREATED);

        assertThat(watchlist.getName()).isEqualTo("Tech");
        assertThat(watchlist.getUserId()).isEqualTo(OWNER);
        assertThat(watchlist.getItems()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "line\nbreak"})
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> Watchlist.create(OWNER, name, CREATED))
                .isInstanceOf(InvalidWatchlistException.class)
                .extracting("code").isEqualTo("INVALID_WATCHLIST_NAME");
        assertThatThrownBy(() -> Watchlist.create(OWNER, "x".repeat(61), CREATED))
                .isInstanceOf(InvalidWatchlistException.class);
    }

    @Test
    void addsAndRemovesStocks() {
        Watchlist watchlist = Watchlist.create(OWNER, "Tech", CREATED);
        UUID stock = UUID.randomUUID();

        WatchlistItem item = watchlist.addStock(stock, "  long term  ", LATER);

        assertThat(item.getNotes()).isEqualTo("long term");
        assertThat(watchlist.contains(stock)).isTrue();
        assertThat(watchlist.getUpdatedAt()).isEqualTo(LATER);

        watchlist.removeStock(stock, LATER.plusSeconds(1));
        assertThat(watchlist.contains(stock)).isFalse();
    }

    @Test
    void aStockAppearsAtMostOnce() {
        Watchlist watchlist = Watchlist.create(OWNER, "Tech", CREATED);
        UUID stock = UUID.randomUUID();
        watchlist.addStock(stock, null, LATER);

        assertThatThrownBy(() -> watchlist.addStock(stock, null, LATER))
                .isInstanceOf(StockAlreadyInWatchlistException.class);
    }

    @Test
    void isLimitedToFiftyStocks() {
        Watchlist watchlist = Watchlist.create(OWNER, "Everything", CREATED);
        IntStream.range(0, Watchlist.MAX_ITEMS).forEach(i -> watchlist.addStock(UUID.randomUUID(), null, LATER));

        assertThatThrownBy(() -> watchlist.addStock(UUID.randomUUID(), null, LATER))
                .isInstanceOf(WatchlistFullException.class)
                .extracting("code").isEqualTo("WATCHLIST_FULL");
    }

    @Test
    void removingOrAnnotatingAMissingStockFails() {
        Watchlist watchlist = Watchlist.create(OWNER, "Tech", CREATED);

        assertThatThrownBy(() -> watchlist.removeStock(UUID.randomUUID(), LATER))
                .isInstanceOf(StockNotInWatchlistException.class);
        assertThatThrownBy(() -> watchlist.changeNotes(UUID.randomUUID(), "x", LATER))
                .isInstanceOf(StockNotInWatchlistException.class);
    }

    @Test
    void notesAreOptionalAndBounded() {
        Watchlist watchlist = Watchlist.create(OWNER, "Tech", CREATED);
        UUID stock = UUID.randomUUID();
        watchlist.addStock(stock, "   ", LATER);

        assertThat(watchlist.changeNotes(stock, " earnings in May ", LATER).getNotes()).isEqualTo("earnings in May");
        assertThatThrownBy(() -> watchlist.changeNotes(stock, "x".repeat(501), LATER))
                .isInstanceOf(InvalidWatchlistException.class);
    }

    @Test
    void renamingToTheSameNameDoesNotTouchTheTimestamp() {
        Watchlist watchlist = Watchlist.create(OWNER, "Tech", CREATED);

        watchlist.rename(" Tech ", LATER);
        assertThat(watchlist.getUpdatedAt()).isEqualTo(CREATED);

        watchlist.rename("Technology", LATER);
        assertThat(watchlist.getName()).isEqualTo("Technology");
        assertThat(watchlist.getUpdatedAt()).isEqualTo(LATER);
    }
}
