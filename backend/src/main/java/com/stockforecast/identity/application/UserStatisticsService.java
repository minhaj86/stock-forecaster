package com.stockforecast.identity.application;

import com.stockforecast.identity.Role;
import com.stockforecast.identity.domain.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Aggregate user metrics for administrators.
 */
@Service
public class UserStatisticsService {

    private static final Duration RECENT_WINDOW = Duration.ofDays(30);

    private final UserAccountRepository accounts;
    private final Clock clock;

    public UserStatisticsService(UserAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserStatistics statistics() {
        return new UserStatistics(
                accounts.count(),
                accounts.countByRole(Role.ADMIN),
                accounts.countByCreatedAtGreaterThanEqual(clock.instant().minus(RECENT_WINDOW)));
    }
}
