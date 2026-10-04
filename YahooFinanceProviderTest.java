package com.stocktracker.api;

import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Market;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YahooFinanceProviderTest {

    private final Company tcs = new Company(5, "TCS", "TCS", Market.NSE, true);

    // 2026-09-30 and 2026-10-01 at 09:15 IST (gmtoffset 19800); the middle day is a holiday (null close)
    private static final String SAMPLE = """
            {"chart":{"result":[{"meta":{"currency":"INR","symbol":"TCS.NS","gmtoffset":19800},
            "timestamp":[1790738700,1790825100,1790911500],
            "indicators":{"quote":[{"open":[3900.0,null,3950.5],"high":[3920.0,null,3990.0],
            "low":[3880.0,null,3940.0],"close":[3910.25,null,3980.75],"volume":[1200000,null,1500000]}]}}],
            "error":null}}""";

    @Test
    void parsesCandlesSkipsHolidaysAndAppliesExchangeTimezone() throws IOException {
        List<DailyPrice> rows = YahooFinanceProvider.parse(tcs, SAMPLE);
        assertEquals(2, rows.size());
        assertEquals(5, rows.get(0).companyId());
        assertEquals(3910.25, rows.get(0).close());
        assertEquals(3980.75, rows.get(1).close());
        assertEquals(1500000, rows.get(1).volume());
        assertEquals(LocalDate.of(2026, 9, 30), rows.get(0).date());
        assertEquals(LocalDate.of(2026, 10, 2), rows.get(1).date());
    }

    @Test
    void apiErrorBecomesIOException() {
        String json = "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data found, symbol may be delisted\"}}}";
        IOException e = assertThrows(IOException.class, () -> YahooFinanceProvider.parse(tcs, json));
        assertTrue(e.getMessage().contains("delisted"));
    }

    @Test
    void missingTimestampsMeansNoTradingDaysInRange() throws IOException {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"gmtoffset\":0},\"indicators\":{\"quote\":[{}]}}],\"error\":null}}";
        assertTrue(YahooFinanceProvider.parse(tcs, json).isEmpty());
    }

    @Test
    void garbageResponseIsRejected() {
        assertThrows(IOException.class, () -> YahooFinanceProvider.parse(tcs, "<html>blocked</html>"));
    }
}
