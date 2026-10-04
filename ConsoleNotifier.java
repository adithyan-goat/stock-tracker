package com.stocktracker.ui;

import com.stocktracker.model.Alert;

import java.util.List;

/** Prints alerts to the console in a way that is hard to miss. */
public final class ConsoleNotifier {

    private ConsoleNotifier() {
    }

    public static synchronized void print(List<Alert> alerts) {
        if (alerts.isEmpty()) return;
        System.out.println();
        System.out.println("################  " + alerts.size() + " NEW ALERT(S)  ################");
        for (Alert a : alerts) {
            System.out.println("[" + a.type().label + "] " + a.tradeDate());
            System.out.println("   " + a.message());
        }
        System.out.println("#####################################################");
        System.out.println();
    }
}
