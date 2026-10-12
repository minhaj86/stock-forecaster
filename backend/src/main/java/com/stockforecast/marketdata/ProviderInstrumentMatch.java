package com.stockforecast.marketdata;

import com.stockforecast.stock.AssetType;

/**
 * A security found by the provider's symbol search, normalised from the vendor format.
 *
 * @param country        ISO 3166 alpha-2 code, or {@code null} if the vendor's country could not be mapped
 * @param instrumentType the vendor's instrument type, for display
 * @param assetType      the catalogue asset type, or {@code null} if the instrument type is not supported
 */
public record ProviderInstrumentMatch(String symbol, String name, String exchange, String mic, String currency,
                                      String country, String instrumentType, AssetType assetType) {
}
