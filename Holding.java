package com.stocktracker.model;

import java.time.LocalDate;

/**
 * One purchase (lot) of a stock. Every purchase is kept separately, even for the same company.
 * The target selling price is always above the buying price.
 */
public record Holding(long id, Company company, int quantity, double buyPrice, LocalDate buyDate,
                      double targetPrice, Status status, Double sellPrice, LocalDate sellDate) {

    public enum Status { OPEN, SOLD }

    public double invested() {
        return buyPrice * quantity;
    }

    /** Profit or loss actually booked; only meaningful for SOLD lots. */
    public double realisedPl() {
        return sellPrice == null ? 0 : (sellPrice - buyPrice) * quantity;
    }

    /** Profit if the lot were sold at the target price. */
    public double targetProfit() {
        return (targetPrice - buyPrice) * quantity;
    }
}
