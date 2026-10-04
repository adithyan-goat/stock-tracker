package com.stocktracker.chart;

/** A horizontal reference line on a chart: a buying price or a selling target. */
public record ChartMarker(Kind kind, double value, String label) {

    public enum Kind {
        BUY('-'),
        TARGET('=');

        public final char fill;

        Kind(char fill) {
            this.fill = fill;
        }
    }
}
