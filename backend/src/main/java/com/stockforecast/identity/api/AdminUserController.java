package com.stockforecast.identity.api;

import com.stockforecast.identity.application.UserStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Administration")
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final UserStatisticsService statisticsService;

    AdminUserController(UserStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "Aggregate user statistics (ADMIN only)")
    @GetMapping("/statistics")
    UserStatisticsResponse statistics() {
        return UserStatisticsResponse.from(statisticsService.statistics());
    }
}
