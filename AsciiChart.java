package com.stocktracker.chart;

import com.stocktracker.model.DailyPrice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text line chart of closing prices for the console.
 * '*' = closing price, '|' = connects big moves, '-' = buying price line, '=' = target price line.
 * Under the chart a strip shows each day's direction: ^ up, v down, = flat.
 */
public final class AsciiChart {

    private static final int LABEL_WIDTH = 10;

    private AsciiChart() {
    }

    public static String render(String title, List<DailyPrice> data, int height, int maxWidth, List<ChartMarker> markers) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n');
        if (data.isEmpty()) {
            return sb.append("(no price data stored yet - run 'Update prices now')").toString();
        }

        List<DailyPrice> pts = downsample(data, maxWidth);
        int n = pts.size();
        int h = Math.max(6, height);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (DailyPrice p : pts) {
            min = Math.min(min, p.close());
            max = Math.max(max, p.close());
        }
        for (ChartMarker m : markers) {
            min = Math.min(min, m.value());
            max = Math.max(max, m.value());
        }
        if (max - min < 1e-9) {
            max += 1;
            min -= 1;
        }
        double range = max - min;

        char[][] grid = new char[h][n];
        for (char[] row : grid) java.util.Arrays.fill(row, ' ');

        for (ChartMarker m : markers) {
            int r = rowOf(m.value(), max, range, h);
            for (int c = 0; c < n; c++) grid[r][c] = m.kind().fill;
        }

        int prevRow = -1;
        for (int c = 0; c < n; c++) {
            int r = rowOf(pts.get(c).close(), max, range, h);
            if (prevRow >= 0) {
                int lo = Math.min(prevRow, r);
                int hi = Math.max(prevRow, r);
                for (int k = lo + 1; k < hi; k++) grid[k][c] = '|';
            }
            grid[r][c] = '*';
            prevRow = r;
        }

        for (int r = 0; r < h; r++) {
            double value = max - r * range / (h - 1);
            sb.append(String.format(Locale.US, "%" + LABEL_WIDTH + ".2f", value)).append(" |")
                    .append(new String(grid[r])).append('\n');
        }

        sb.append(" ".repeat(LABEL_WIDTH + 1)).append('+').append("-".repeat(n)).append('\n');

        String left = pts.get(0).date().toString();
        String right = pts.get(n - 1).date().toString();
        int pad = Math.max(1, n - left.length() - right.length());
        sb.append(" ".repeat(LABEL_WIDTH + 2)).append(left).append(" ".repeat(pad)).append(right).append('\n');

        StringBuilder strip = new StringBuilder();
        for (int c = 0; c < n; c++) {
            if (c == 0) {
                strip.append('.');
            } else {
                double a = pts.get(c - 1).close();
                double b = pts.get(c).close();
                strip.append(b > a ? '^' : b < a ? 'v' : '=');
            }
        }
        sb.append(" ".repeat(LABEL_WIDTH + 2)).append(strip).append("   (^ up, v down, = flat)\n");

        double first = data.get(0).close();
        double last = data.get(data.size() - 1).close();
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (DailyPrice p : data) {
            lo = Math.min(lo, p.close());
            hi = Math.max(hi, p.close());
        }
        sb.append(String.format(Locale.US, "Low %.2f | High %.2f | Last %.2f | Change over period %+.2f%% (%d trading days)%n",
                lo, hi, last, (last - first) / first * 100.0, data.size()));

        for (ChartMarker m : markers) {
            sb.append(String.format(Locale.US, "  %c%c%c%c  %s: %.2f%n", m.kind().fill, m.kind().fill, m.kind().fill,
                    m.kind().fill, m.label(), m.value()));
        }
        return sb.toString();
    }

    private static int rowOf(double value, double max, double range, int h) {
        int r = (int) Math.round((max - value) / range * (h - 1));
        return Math.max(0, Math.min(h - 1, r));
    }

    /** Keeps the chart inside the console width by sampling evenly (first and last day are always kept). */
    static List<DailyPrice> downsample(List<DailyPrice> data, int maxWidth) {
        if (data.size() <= maxWidth || maxWidth < 2) return data;
        List<DailyPrice> out = new ArrayList<>(maxWidth);
        for (int i = 0; i < maxWidth; i++) {
            int idx = (int) Math.round(i * (data.size() - 1) / (double) (maxWidth - 1));
            out.add(data.get(idx));
        }
        return out;
    }
}
