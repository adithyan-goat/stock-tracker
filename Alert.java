package com.stocktracker.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Alert(long id, long holdingId, long companyId, AlertType type, LocalDate tradeDate,
                    String message, LocalDateTime createdAt, boolean read) {

    /** New, not yet stored alert. */
    public static Alert create(long holdingId, long companyId, AlertType type, LocalDate tradeDate, String message) {
        return new Alert(0, holdingId, companyId, type, tradeDate, message, LocalDateTime.now(), false);
    }
}
