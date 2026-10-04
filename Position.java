package com.stocktracker.model;

/** An open lot together with the latest two stored prices of its company. */
public record Position(Holding lot, DailyPrice last, DailyPrice prev) {

    public enum Status { NO_DATA, TARGET_HIT, BELOW_BUY, OK }

    public boolean hasPrice() {
        return last != null;
    }

    public double price() {
        return last.close();
    }

    public double currentValue() {
        return hasPrice() ? price() * lot.quantity() : 0;
    }

    public double unrealisedPl() {
        return hasPrice() ? (price() - lot.buyPrice()) * lot.quantity() : 0;
    }

    public double unrealisedPct() {
        return hasPrice() ? (price() - lot.buyPrice()) / lot.buyPrice() * 100.0 : 0;
    }

    /** Change versus the previous stored day, or null when unknown. */
    public Double dayChangePct() {
        if (last == null || prev == null || prev.close() == 0) return null;
        return (last.close() - prev.close()) / prev.close() * 100.0;
    }

    public Status status() {
        if (!hasPrice()) return Status.NO_DATA;
        if (price() >= lot.targetPrice()) return Status.TARGET_HIT;
        if (price() < lot.buyPrice()) return Status.BELOW_BUY;
        return Status.OK;
    }
}
