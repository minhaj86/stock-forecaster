package com.stockforecast.marketdata;

import com.stockforecast.stock.AssetType;

/**
 * Listing metadata supplied by the provider for registering a stock. Sector and industry are {@code null} when the
 * provider (or the subscribed plan) does not supply them.
 */
public record ProviderStockMetadata(String symbol, String name, String exchange, String mic, String currency,
                                    String country, String instrumentType, AssetType assetType, String sector,
                                    String industry) {
}
