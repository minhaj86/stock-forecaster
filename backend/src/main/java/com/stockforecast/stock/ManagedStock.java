package com.stockforecast.stock;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrative view of a catalogue entry, including inactive ones.
 *
 * @param source where the metadata came from: {@code CATALOGUE}, {@code MANUAL} or a market-data provider id
 */
public record ManagedStock(UUID id, String symbol, String companyName, String exchange, String mic, String currency,
                           String country, String sector, String industry, AssetType assetType, boolean active,
                           String source, Instant createdAt, Instant updatedAt) {
}
