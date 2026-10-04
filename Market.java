package com.stocktracker.model;

/** Supported markets. The suffix is what the price API expects after the ticker. */
public enum Market {
    NSE("INR", "Rs ", ".NS"),
    BSE("INR", "Rs ", ".BO"),
    US("USD", "$", "");

    public final String currency;
    public final String currencySymbol;
    public final String apiSuffix;

    Market(String currency, String currencySymbol, String apiSuffix) {
        this.currency = currency;
        this.currencySymbol = currencySymbol;
        this.apiSuffix = apiSuffix;
    }

    /** Returns null when the text is not a known market. */
    public static Market tryParse(String text) {
        if (text == null) return null;
        switch (text.trim().toUpperCase()) {
            case "NSE": case "NS": case "NSI": return NSE;
            case "BSE": case "BO": return BSE;
            case "US": case "USA": case "NASDAQ": case "NYSE": case "AMEX": return US;
            default: return null;
        }
    }
}
