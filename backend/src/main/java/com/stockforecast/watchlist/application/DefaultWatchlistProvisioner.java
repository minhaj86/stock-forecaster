package com.stockforecast.watchlist.application;

import com.stockforecast.identity.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the starter watchlist inside the registration transaction (see the profile provisioner for the
 * synchronous-listener trade-off).
 */
@Component
class DefaultWatchlistProvisioner {

    private final WatchlistService watchlistService;

    DefaultWatchlistProvisioner(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @EventListener
    void on(UserRegisteredEvent event) {
        watchlistService.createDefaultWatchlist(event.userId());
    }
}
