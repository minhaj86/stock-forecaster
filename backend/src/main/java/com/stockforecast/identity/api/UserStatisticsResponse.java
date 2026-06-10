package com.stockforecast.identity.api;

import com.stockforecast.identity.application.UserStatistics;

record UserStatisticsResponse(long totalUsers, long administrators, long registeredLast30Days) {

    static UserStatisticsResponse from(UserStatistics statistics) {
        return new UserStatisticsResponse(statistics.totalUsers(), statistics.administrators(),
                statistics.registeredLast30Days());
    }
}
