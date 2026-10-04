package com.stocktracker.model;

import java.time.LocalDate;

public record DailyPrice(long companyId, LocalDate date, double open, double high, double low, double close, long volume) {
}
