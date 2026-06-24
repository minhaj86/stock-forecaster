package com.stockforecast.stock.domain;

import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.StockRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A canonical security. Exactly one row exists per listing, regardless of how many users watch it.
 */
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(length = 255)
    private String aliases;

    @Column(nullable = false, length = 40)
    private String exchange;

    @Column(nullable = false, length = 10)
    private String mic;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(length = 80)
    private String sector;

    @Column(length = 120)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Stock() {
        // required by JPA
    }

    public StockRef toRef() {
        return new StockRef(id, symbol, companyName, exchange, mic, currency, country, sector, industry, assetType);
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Stock stock && id.equals(stock.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Stock[" + symbol + "]";
    }
}
