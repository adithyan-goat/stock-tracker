package com.stocktracker.service;

import com.stocktracker.model.Alert;
import com.stocktracker.model.AlertType;
import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;
import com.stocktracker.model.Market;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertEvaluatorTest {

    private final Company tcs = new Company(1, "TCS", "Tata Consultancy Services", Market.NSE, true);
    // bought 10 shares at 3800 on 1 Sep, target 4200
    private final Holding lot = new Holding(7, tcs, 10, 3800, LocalDate.of(2026, 9, 1), 4200,
            Holding.Status.OPEN, null, null);

    private DailyPrice px(String date, double close) {
        return new DailyPrice(1, LocalDate.parse(date), close, close, close, close, 1000);
    }

    private List<AlertType> types(List<Alert> alerts) {
        return alerts.stream().map(Alert::type).toList();
    }

    @Test
    void anyDecreaseFromPreviousDayRaisesDailyDropOnly() {
        List<Alert> a = AlertEvaluator.evaluate(lot, px("2026-10-02", 4000), px("2026-10-03", 3999.5));
        assertEquals(List.of(AlertType.DAILY_DROP), types(a));
        assertTrue(a.get(0).message().contains("TCS fell"));
    }

    @Test
    void noAlertWhenPriceRisesAndStaysBetweenBuyAndTarget() {
        assertTrue(AlertEvaluator.evaluate(lot, px("2026-10-02", 3900), px("2026-10-03", 3950)).isEmpty());
    }

    @Test
    void belowBuyPriceAlertIncludesBuyMoreSuggestion() {
        List<Alert> a = AlertEvaluator.evaluate(lot, px("2026-10-02", 3700), px("2026-10-03", 3600));
        assertEquals(List.of(AlertType.DAILY_DROP, AlertType.BELOW_BUY_PRICE), types(a));
        String msg = a.get(1).message();
        assertTrue(msg.contains("below your buying price"));
        assertTrue(msg.contains("average cost to Rs 3,700.00"), msg);   // (3800 + 3600) / 2
    }

    @Test
    void targetReachedReportsProfitForQuantityHeld() {
        List<Alert> a = AlertEvaluator.evaluate(lot, px("2026-10-02", 4100), px("2026-10-03", 4250));
        assertEquals(List.of(AlertType.TARGET_REACHED), types(a));
        // (4250 - 3800) * 10 = 4500
        assertTrue(a.get(0).message().contains("+Rs 4,500.00"), a.get(0).message());
    }

    @Test
    void pricesBeforeThePurchaseDateAreIgnored() {
        assertTrue(AlertEvaluator.evaluate(lot, px("2026-08-28", 3000), px("2026-08-29", 2900)).isEmpty());
    }

    @Test
    void firstEverDayHasNoDropAlert() {
        assertTrue(AlertEvaluator.evaluate(lot, null, px("2026-10-03", 3900)).isEmpty());
    }

    @Test
    void purchaseRejectsTargetAtOrBelowBuyPrice() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> HoldingService.validatePurchase(5, 100, 100, d, tcs));
        assertThrows(IllegalArgumentException.class, () -> HoldingService.validatePurchase(5, 100, 90, d, tcs));
        HoldingService.validatePurchase(5, 100, 100.01, d, tcs);   // fine
    }

    @Test
    void purchaseRejectsBadQuantityPriceAndFutureDate() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> HoldingService.validatePurchase(0, 100, 110, d, tcs));
        assertThrows(IllegalArgumentException.class, () -> HoldingService.validatePurchase(5, 0, 110, d, tcs));
        assertThrows(IllegalArgumentException.class,
                () -> HoldingService.validatePurchase(5, 100, 110, LocalDate.now().plusDays(1), tcs));
    }
}
