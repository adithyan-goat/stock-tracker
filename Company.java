package com.stocktracker.model;

public record Company(long id, String symbol, String name, Market market, boolean active) {

    /** Ticker as the price API expects it, e.g. TCS.NS or AAPL. */
    public String apiSymbol() {
        return symbol + market.apiSuffix;
    }

    public String label() {
        return symbol + " (" + market + ") - " + name;
    }
}
