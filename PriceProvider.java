package com.stocktracker.api;

import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;

import java.io.IOException;
import java.util.List;

/** Source of daily prices. Implement this interface to plug in a different API. */
public interface PriceProvider {

    /**
     * Daily bars for the company, oldest first.
     *
     * @param range how far back to go: 5d, 1mo, 3mo, 6mo, 1y, 5y ...
     */
    List<DailyPrice> fetchDaily(Company company, String range) throws IOException, InterruptedException;
}
