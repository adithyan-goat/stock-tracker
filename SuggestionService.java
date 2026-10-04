package com.stocktracker.service;

import com.stocktracker.model.Position;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Finds open lots whose stock now trades below the buying price (candidates to buy more). */
public final class SuggestionService {

    public record Suggestion(Position position, double belowPct, double avgCostIfSameQtyBought) {
    }

    private final HoldingService holdingService;

    public SuggestionService(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    public List<Suggestion> belowBuyPrice() throws SQLException {
        List<Suggestion> out = new ArrayList<>();
        for (Position p : holdingService.positions()) {
            if (p.status() != Position.Status.BELOW_BUY) continue;
            double below = (p.lot().buyPrice() - p.price()) / p.lot().buyPrice() * 100.0;
            double avg = (p.lot().buyPrice() + p.price()) / 2.0;
            out.add(new Suggestion(p, below, avg));
        }
        out.sort((a, b) -> Double.compare(b.belowPct(), a.belowPct()));
        return out;
    }
}
