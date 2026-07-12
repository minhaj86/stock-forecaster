package com.stockforecast.watchlist.api;

import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistItemView;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistSummaryView;
import com.stockforecast.watchlist.application.WatchlistViews.WatchlistView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response bodies of the watchlist API.
 */
final class WatchlistDtos {

    private WatchlistDtos() {
    }

    record WatchlistNameRequest(@NotBlank @Size(max = 60) @Schema(example = "Technology") String name) {
    }

    record AddStockRequest(@NotBlank @Size(max = 20) @Schema(example = "MSFT") String symbol,
                           @Size(max = 500) String notes) {
    }

    record NotesRequest(@Size(max = 500) String notes) {
    }

    record WatchlistSummaryResponse(UUID id, String name, int stockCount,
                                    @Schema(description = "Symbols in the order they were added (at most 50)") List<String> symbols,
                                    Instant createdAt, Instant updatedAt) {

        static WatchlistSummaryResponse from(WatchlistSummaryView view) {
            return new WatchlistSummaryResponse(view.id(), view.name(), view.stockCount(), view.symbols(),
                    view.createdAt(), view.updatedAt());
        }
    }

    record WatchlistResponse(UUID id, String name, int stockCount, Instant createdAt, Instant updatedAt,
                             List<WatchlistItemResponse> items) {

        static WatchlistResponse from(WatchlistView view) {
            return new WatchlistResponse(view.id(), view.name(), view.items().size(), view.createdAt(),
                    view.updatedAt(), view.items().stream().map(WatchlistItemResponse::from).toList());
        }
    }

    record WatchlistItemResponse(StockSummary stock, Instant addedAt, String notes) {

        static WatchlistItemResponse from(WatchlistItemView view) {
            return new WatchlistItemResponse(StockSummary.from(view.stock()), view.addedAt(), view.notes());
        }
    }

    record StockSummary(String symbol, String companyName, String exchange, String currency, String sector,
                        AssetType assetType) {

        static StockSummary from(StockRef stock) {
            return new StockSummary(stock.symbol(), stock.companyName(), stock.exchange(), stock.currency(),
                    stock.sector(), stock.assetType());
        }
    }
}
