package com.stocktracker.model;

public enum AlertType {
    DAILY_DROP("PRICE DROP"),
    BELOW_BUY_PRICE("BELOW BUY PRICE"),
    TARGET_REACHED("TARGET REACHED");

    public final String label;

    AlertType(String label) {
        this.label = label;
    }
}
