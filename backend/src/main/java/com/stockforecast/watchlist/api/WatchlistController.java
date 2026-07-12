package com.stockforecast.watchlist.api;

import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.watchlist.api.WatchlistDtos.AddStockRequest;
import com.stockforecast.watchlist.api.WatchlistDtos.NotesRequest;
import com.stockforecast.watchlist.api.WatchlistDtos.WatchlistItemResponse;
import com.stockforecast.watchlist.api.WatchlistDtos.WatchlistNameRequest;
import com.stockforecast.watchlist.api.WatchlistDtos.WatchlistResponse;
import com.stockforecast.watchlist.api.WatchlistDtos.WatchlistSummaryResponse;
import com.stockforecast.watchlist.application.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The owner is always the authenticated user: a watchlist id of another user answers 404.
 */
@Tag(name = "Watchlists")
@RestController
@RequestMapping("/api/v1/watchlists")
class WatchlistController {

    private final WatchlistService watchlistService;

    WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @Operation(summary = "List my watchlists")
    @GetMapping
    List<WatchlistSummaryResponse> list(@CurrentUser AuthenticatedUser user) {
        return watchlistService.list(user.id()).stream().map(WatchlistSummaryResponse::from).toList();
    }

    @Operation(summary = "Create a watchlist")
    @PostMapping
    ResponseEntity<WatchlistResponse> create(@CurrentUser AuthenticatedUser user,
                                             @Valid @RequestBody WatchlistNameRequest request) {
        WatchlistResponse created = WatchlistResponse.from(watchlistService.create(user.id(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/watchlists/" + created.id())).body(created);
    }

    @Operation(summary = "Get a watchlist with its stocks")
    @GetMapping("/{watchlistId}")
    WatchlistResponse get(@CurrentUser AuthenticatedUser user, @PathVariable UUID watchlistId) {
        return WatchlistResponse.from(watchlistService.get(user.id(), watchlistId));
    }

    @Operation(summary = "Rename a watchlist")
    @PatchMapping("/{watchlistId}")
    WatchlistResponse rename(@CurrentUser AuthenticatedUser user, @PathVariable UUID watchlistId,
                             @Valid @RequestBody WatchlistNameRequest request) {
        return WatchlistResponse.from(watchlistService.rename(user.id(), watchlistId, request.name()));
    }

    @Operation(summary = "Delete a watchlist")
    @DeleteMapping("/{watchlistId}")
    ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable UUID watchlistId) {
        watchlistService.delete(user.id(), watchlistId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a stock to a watchlist",
            description = "Market data for the stock is collected in the background from then on.")
    @PostMapping("/{watchlistId}/items")
    ResponseEntity<WatchlistItemResponse> addStock(@CurrentUser AuthenticatedUser user,
                                                   @PathVariable UUID watchlistId,
                                                   @Valid @RequestBody AddStockRequest request) {
        WatchlistItemResponse item = WatchlistItemResponse.from(
                watchlistService.addStock(user.id(), watchlistId, request.symbol(), request.notes()));
        return ResponseEntity.created(URI.create(
                "/api/v1/watchlists/%s/items/%s".formatted(watchlistId, item.stock().symbol()))).body(item);
    }

    @Operation(summary = "Change the notes of a watchlist entry")
    @PatchMapping("/{watchlistId}/items/{symbol}")
    WatchlistItemResponse changeNotes(@CurrentUser AuthenticatedUser user, @PathVariable UUID watchlistId,
                                      @PathVariable String symbol, @Valid @RequestBody NotesRequest request) {
        return WatchlistItemResponse.from(
                watchlistService.changeNotes(user.id(), watchlistId, symbol, request.notes()));
    }

    @Operation(summary = "Remove a stock from a watchlist")
    @DeleteMapping("/{watchlistId}/items/{symbol}")
    ResponseEntity<Void> removeStock(@CurrentUser AuthenticatedUser user, @PathVariable UUID watchlistId,
                                     @PathVariable String symbol) {
        watchlistService.removeStock(user.id(), watchlistId, symbol);
        return ResponseEntity.noContent().build();
    }
}
