package com.stockforecast.stock.api;

import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;

record StockResponse(String symbol, String companyName, String exchange, String mic, String currency, String country,
                     String sector, String industry, AssetType assetType) {

    static StockResponse from(StockRef stock) {
        return new StockResponse(stock.symbol(), stock.companyName(), stock.exchange(), stock.mic(), stock.currency(),
                stock.country(), stock.sector(), stock.industry(), stock.assetType());
    }
}
