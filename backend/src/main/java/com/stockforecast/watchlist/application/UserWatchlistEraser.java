package com.stockforecast.watchlist.application;

import com.stockforecast.identity.UserAccountDeleted;
import com.stockforecast.watchlist.domain.WatchlistRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deletes a user's watchlists (and their items) inside the account-deletion transaction. Stocks and their market
 * data are shared by all users and stay untouched.
 */
@Component
class UserWatchlistEraser {

    private final WatchlistRepository watchlists;

    UserWatchlistEraser(WatchlistRepository watchlists) {
        this.watchlists = watchlists;
    }

    @EventListener
    void on(UserAccountDeleted event) {
        watchlists.findAllByUserIdOrderByCreatedAtAsc(event.userId()).forEach(watchlists::delete);
    }
}
