package com.stocktracker.chart;

import com.stocktracker.model.DailyPrice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiChartTest {

    private List<DailyPrice> series(double... closes) {
        List<DailyPrice> out = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 9, 1);
        for (double c : closes) {
            out.add(new DailyPrice(1, d, c, c, c, c, 0));
            d = d.plusDays(1);
        }
        return out;
    }

    @Test
    void emptyDataGivesHelpfulMessage() {
        String s = AsciiChart.render("T", List.of(), 10, 50, List.of());
        assertTrue(s.contains("no price data"));
    }

    @Test
    void drawsOneStarPerDayAndMarksUpAndDownDays() {
        String s = AsciiChart.render("T", series(100, 105, 103, 110), 8, 50, List.of());
        long stars = s.chars().filter(c -> c == '*').count();
        assertEquals(4, stars);
        assertTrue(s.contains(".^v^"), s);
    }

    @Test
    void buyAndTargetLinesAreDrawnAndListed() {
        List<ChartMarker> m = List.of(
                new ChartMarker(ChartMarker.Kind.BUY, 98, "Buy price lot #1"),
                new ChartMarker(ChartMarker.Kind.TARGET, 120, "Target lot #1"));
        String s = AsciiChart.render("T", series(100, 105, 103, 110), 10, 50, m);
        assertTrue(s.contains("----"));
        assertTrue(s.contains("===="));
        assertTrue(s.contains("Buy price lot #1: 98.00"));
        assertTrue(s.contains("Target lot #1: 120.00"));
    }

    @Test
    void longHistoryIsSampledToConsoleWidthKeepingFirstAndLastDay() {
        double[] closes = new double[300];
        for (int i = 0; i < closes.length; i++) closes[i] = 100 + i;
        List<DailyPrice> data = series(closes);
        List<DailyPrice> sampled = AsciiChart.downsample(data, 80);
        assertEquals(80, sampled.size());
        assertEquals(data.get(0), sampled.get(0));
        assertEquals(data.get(299), sampled.get(79));
    }

    @Test
    void flatSeriesDoesNotCrash() {
        String s = AsciiChart.render("T", series(50, 50, 50), 8, 50, List.of());
        assertTrue(s.contains("*"));
    }
}
