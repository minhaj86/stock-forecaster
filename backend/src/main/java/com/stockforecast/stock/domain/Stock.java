package com.stockforecast.stock.domain;

import com.stockforecast.shared.domain.Identifiers;
import com.stockforecast.stock.AssetType;
import com.stockforecast.stock.ManagedStock;
import com.stockforecast.stock.StockRef;
import com.stockforecast.stock.domain.StockAdministrationExceptions.InvalidStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A canonical security. Exactly one row exists per listing, regardless of how many users watch it.
 */
@Entity
@Table(name = "stocks")
public class Stock {

    public static final String CATALOGUE_SOURCE = "CATALOGUE";
    public static final String MANUAL_SOURCE = "MANUAL";

    private static final Pattern MIC = Pattern.compile("[A-Z0-9]{4}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

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

    /** Where the metadata came from: {@value #CATALOGUE_SOURCE}, {@value #MANUAL_SOURCE} or a provider id. */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Stock() {
        // required by JPA
    }

    /**
     * Registers a security with validated, normalised metadata (codes upper-cased, blanks trimmed).
     */
    public static Stock register(StockSymbol symbol, String companyName, String exchange, String mic, String currency,
                                 String country, String sector, String industry, AssetType assetType, String source,
                                 Instant now) {
        Stock stock = new Stock();
        stock.id = Identifiers.newId();
        stock.symbol = Objects.requireNonNull(symbol, "symbol").value();
        stock.companyName = requireText(companyName, 200, "Company name");
        stock.exchange = requireText(exchange, 40, "Exchange");
        stock.mic = requireCode(mic, MIC, "MIC must be a 4-character ISO 10383 market identifier code");
        stock.currency = requireCode(currency, CURRENCY, "Currency must be a 3-letter ISO 4217 code");
        stock.country = requireCode(country, COUNTRY, "Country must be a 2-letter ISO 3166 code");
        stock.sector = optionalText(sector, 80, "Sector");
        stock.industry = optionalText(industry, 120, "Industry");
        if (assetType == null) {
            throw new InvalidStockException("Asset type is required");
        }
        stock.assetType = assetType;
        if (source == null || !SOURCE.matcher(source).matches()) {
            throw new InvalidStockException("Metadata source is invalid");
        }
        stock.source = source;
        stock.active = true;
        stock.createdAt = Objects.requireNonNull(now, "now");
        stock.updatedAt = now;
        return stock;
    }

    public void activate(Instant now) {
        if (!active) {
            this.active = true;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public void deactivate(Instant now) {
        if (active) {
            this.active = false;
            this.updatedAt = Objects.requireNonNull(now, "now");
        }
    }

    public ManagedStock toManaged() {
        return new ManagedStock(id, symbol, companyName, exchange, mic, currency, country, sector, industry, assetType,
                active, source, createdAt, updatedAt);
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

    public String getSource() {
        return source;
    }

    private static String requireText(String value, int maxLength, String label) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > maxLength || stripped.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidStockException("%s must be 1-%d printable characters".formatted(label, maxLength));
        }
        return stripped;
    }

    private static String optionalText(String value, int maxLength, String label) {
        return value == null || value.isBlank() ? null : requireText(value, maxLength, label);
    }

    private static String requireCode(String value, Pattern format, String message) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!format.matcher(normalized).matches()) {
            throw new InvalidStockException(message);
        }
        return normalized;
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
