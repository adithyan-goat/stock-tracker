package com.stocktracker.util;

import com.stocktracker.model.Market;

import java.util.Locale;

/** Small formatting helpers (Locale.US so decimals always print with a dot). */
public final class Fmt {

    private Fmt() {
    }

    public static String money(Market m, double v) {
        String body = String.format(Locale.US, "%,.2f", Math.abs(v));
        return (v < -0.005 ? "-" : "") + m.currencySymbol + body;
    }

    public static String signedMoney(Market m, double v) {
        String body = String.format(Locale.US, "%,.2f", Math.abs(v));
        return (v < -0.005 ? "-" : "+") + m.currencySymbol + body;
    }

    public static String pct(double v) {
        return String.format(Locale.US, "%+.2f%%", v);
    }

    public static String num(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    public static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
