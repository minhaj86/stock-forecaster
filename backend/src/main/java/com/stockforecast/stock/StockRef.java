package com.stockforecast.stock;

import java.io.Serializable;
import java.util.UUID;

/**
 * Read-only view of a catalogue entry, shared with other modules. Serializable because it is cached.
 */
public record StockRef(UUID id, String symbol, String companyName, String exchange, String mic, String currency,
                       String country, String sector, String industry, AssetType assetType) implements Serializable {
}
