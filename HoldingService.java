package com.stocktracker.service;

import com.stocktracker.db.HoldingDao;
import com.stocktracker.db.PriceDao;
import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;
import com.stocktracker.model.Position;
import com.stocktracker.util.Fmt;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class HoldingService {

    private final HoldingDao holdings;
    private final PriceDao prices;

    public HoldingService(HoldingDao holdings, PriceDao prices) {
        this.holdings = holdings;
        this.prices = prices;
    }

    /** The core rule of the project: the selling target must always be above the buying price. */
    public static void validatePurchase(int quantity, double buyPrice, double target, LocalDate buyDate, Company c) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (buyPrice <= 0) throw new IllegalArgumentException("Buying price must be greater than zero.");
        if (target <= buyPrice) {
            throw new IllegalArgumentException("Target selling price (" + Fmt.money(c.market(), target)
                    + ") must be higher than the buying price (" + Fmt.money(c.market(), buyPrice)
                    + ") so the trade cannot end in a loss.");
        }
        if (buyDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("Buying date cannot be in the future.");
    }

    public Holding addPurchase(Company c, int quantity, double buyPrice, LocalDate buyDate, double target) throws SQLException {
        validatePurchase(quantity, buyPrice, target, buyDate, c);
        long id = holdings.insert(new Holding(0, c, quantity, buyPrice, buyDate, target, Holding.Status.OPEN, null, null));
        return holdings.findById(id).orElseThrow();
    }

    public Holding sell(long holdingId, double sellPrice, LocalDate date) throws SQLException {
        Holding h = holdings.findById(holdingId)
                .orElseThrow(() -> new IllegalArgumentException("No holding with id " + holdingId));
        if (h.status() != Holding.Status.OPEN) throw new IllegalArgumentException("That lot is already sold.");
        if (sellPrice <= 0) throw new IllegalArgumentException("Selling price must be greater than zero.");
        if (date.isBefore(h.buyDate())) throw new IllegalArgumentException("Selling date cannot be before the buying date.");
        if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("Selling date cannot be in the future.");
        holdings.markSold(holdingId, sellPrice, date);
        return holdings.findById(holdingId).orElseThrow();
    }

    public void changeTarget(long holdingId, double newTarget) throws SQLException {
        Holding h = holdings.findById(holdingId)
                .orElseThrow(() -> new IllegalArgumentException("No holding with id " + holdingId));
        if (h.status() != Holding.Status.OPEN) throw new IllegalArgumentException("That lot is already sold.");
        if (newTarget <= h.buyPrice()) {
            throw new IllegalArgumentException("Target must stay above the buying price ("
                    + Fmt.money(h.company().market(), h.buyPrice()) + ").");
        }
        holdings.updateTarget(holdingId, newTarget);
    }

    /** Open lots with their latest stored prices. */
    public List<Position> positions() throws SQLException {
        List<Position> out = new ArrayList<>();
        for (Holding h : holdings.findOpen()) {
            List<DailyPrice> last2 = prices.latest(h.company().id(), 2);
            DailyPrice last = last2.isEmpty() ? null : last2.get(last2.size() - 1);
            DailyPrice prev = last2.size() > 1 ? last2.get(0) : null;
            out.add(new Position(h, last, prev));
        }
        return out;
    }

    public List<Holding> soldLots() throws SQLException {
        return holdings.findByStatus(Holding.Status.SOLD);
    }
}
