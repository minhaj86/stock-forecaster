package com.stockforecast.marketdata.domain;

import com.stockforecast.marketdata.MarketDataJobStatus;
import com.stockforecast.marketdata.MarketDataJobTrigger;
import com.stockforecast.marketdata.MarketDataJobType;

import java.util.List;
import java.util.UUID;

/**
 * Filtered, paged job history (newest first). Every filter is optional.
 */
public interface MarketDataJobSearch {

    SearchPage search(Criteria criteria);

    record Criteria(MarketDataJobStatus status, MarketDataJobType type, MarketDataJobTrigger trigger, UUID stockId,
                    int page, int size) {
    }

    record SearchPage(List<MarketDataJob> content, long totalElements) {
    }
}
