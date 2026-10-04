package com.stocktracker.service;

import com.stocktracker.db.AlertDao;
import com.stocktracker.db.HoldingDao;
import com.stocktracker.db.PriceDao;
import com.stocktracker.model.Alert;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Checks every open lot against its latest two stored prices and stores new alerts. */
public final class AlertService {

    private final HoldingDao holdings;
    private final PriceDao prices;
    private final AlertDao alerts;

    public AlertService(HoldingDao holdings, PriceDao prices, AlertDao alerts) {
        this.holdings = holdings;
        this.prices = prices;
        this.alerts = alerts;
    }

    /** Returns only alerts that were newly created (the same alert is never stored twice for one day). */
    public List<Alert> evaluateAll() throws SQLException {
        List<Alert> created = new ArrayList<>();
        for (Holding h : holdings.findOpen()) {
            List<DailyPrice> last2 = prices.latest(h.company().id(), 2);
            if (last2.isEmpty()) continue;
            DailyPrice today = last2.get(last2.size() - 1);
            DailyPrice prev = last2.size() > 1 ? last2.get(0) : null;
            for (Alert a : AlertEvaluator.evaluate(h, prev, today)) {
                if (alerts.insertIfNew(a)) created.add(a);
            }
        }
        return created;
    }
}
