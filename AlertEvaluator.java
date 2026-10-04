package com.stocktracker.service;

import com.stocktracker.model.Alert;
import com.stocktracker.model.AlertType;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;
import com.stocktracker.model.Market;
import com.stocktracker.util.Fmt;

import java.util.ArrayList;
import java.util.List;

/**
 * The alert rules, kept free of any database code so they are easy to test.
 * <ul>
 *   <li>DAILY_DROP: today's close is lower than the previous day's close (any decrease).</li>
 *   <li>BELOW_BUY_PRICE: today's close is lower than the price the lot was bought at, with a buy-more hint.</li>
 *   <li>TARGET_REACHED: today's close is at or above the selling target, with the profit for the quantity held.</li>
 * </ul>
 */
public final class AlertEvaluator {

    private AlertEvaluator() {
    }

    public static List<Alert> evaluate(Holding h, DailyPrice prev, DailyPrice today) {
        List<Alert> out = new ArrayList<>();
        if (today == null || today.date().isBefore(h.buyDate())) return out;

        Market m = h.company().market();
        String sym = h.company().symbol();
        double px = today.close();
        int qty = h.quantity();
        double pl = (px - h.buyPrice()) * qty;
        double plPct = (px - h.buyPrice()) / h.buyPrice() * 100.0;

        // 1) price lower than the previous day
        if (prev != null && prev.close() > 0 && px < prev.close()) {
            double chg = (px - prev.close()) / prev.close() * 100.0;
            String msg = String.format("%s fell %.2f%% on %s (%s -> %s). Your lot #%d: %d shares bought at %s, "
                            + "unrealised P/L %s (%s).",
                    sym, Math.abs(chg), today.date(), Fmt.money(m, prev.close()), Fmt.money(m, px),
                    h.id(), qty, Fmt.money(m, h.buyPrice()), Fmt.signedMoney(m, pl), Fmt.pct(plPct));
            out.add(Alert.create(h.id(), h.company().id(), AlertType.DAILY_DROP, today.date(), msg));
        }

        // 2) price below what the user paid
        if (px < h.buyPrice()) {
            double newAvg = (h.buyPrice() + px) / 2.0;
            String msg = String.format("%s is %.2f%% below your buying price (%s now vs %s) for lot #%d. "
                            + "Unrealised loss: %s. Suggestion: it is cheaper than your purchase - buying %d more shares "
                            + "now would bring your average cost to %s. Informational only, not financial advice.",
                    sym, Math.abs(plPct), Fmt.money(m, px), Fmt.money(m, h.buyPrice()), h.id(),
                    Fmt.money(m, Math.abs(pl)), qty, Fmt.money(m, newAvg));
            out.add(Alert.create(h.id(), h.company().id(), AlertType.BELOW_BUY_PRICE, today.date(), msg));
        }

        // 3) price reached the selling target
        if (px >= h.targetPrice()) {
            String msg = String.format("TARGET REACHED: %s closed at %s, at or above your selling target %s (lot #%d). "
                            + "Selling %d shares now locks in a profit of %s (%s) over your buying price of %s.",
                    sym, Fmt.money(m, px), Fmt.money(m, h.targetPrice()), h.id(), qty,
                    Fmt.signedMoney(m, pl), Fmt.pct(plPct), Fmt.money(m, h.buyPrice()));
            out.add(Alert.create(h.id(), h.company().id(), AlertType.TARGET_REACHED, today.date(), msg));
        }
        return out;
    }
}
