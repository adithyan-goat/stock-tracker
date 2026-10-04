package com.stocktracker.chart;

import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Draws a proper graphical price chart to a PNG file (works on machines without a display). */
public final class ChartImageExporter {

    private ChartImageExporter() {
    }

    public static Path export(Company company, List<DailyPrice> data, List<ChartMarker> markers, Path dir) throws IOException {
        if (data.isEmpty()) throw new IOException("No price data stored for " + company.symbol() + " yet.");
        Files.createDirectories(dir);

        final int w = 1100, h = 560, left = 90, right = 50, top = 70, bottom = 70;
        final int plotW = w - left - right;
        final int plotH = h - top - bottom;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (DailyPrice p : data) {
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
        double pad = (max - min) * 0.06;
        min -= pad;
        max += pad;
        final double lo = min, hi = max;

        g.setColor(new Color(30, 30, 30));
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString(company.symbol() + " (" + company.market() + ")  " + company.name(), left, 35);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(Color.GRAY);
        DailyPrice first = data.get(0);
        DailyPrice last = data.get(data.size() - 1);
        double chg = (last.close() - first.close()) / first.close() * 100.0;
        g.drawString(String.format(Locale.US, "%s to %s   |   last close %.2f %s   |   change %+.2f%%",
                first.date(), last.date(), last.close(), company.market().currency, chg), left, 55);

        // grid + y labels
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i <= 6; i++) {
            double v = lo + (hi - lo) * i / 6.0;
            int y = top + plotH - (int) Math.round(plotH * i / 6.0);
            g.setColor(new Color(228, 228, 228));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(left, y, left + plotW, y);
            g.setColor(Color.DARK_GRAY);
            String s = String.format(Locale.US, "%.2f", v);
            g.drawString(s, left - 8 - fm.stringWidth(s), y + 4);
        }
        g.setColor(Color.DARK_GRAY);
        g.drawRect(left, top, plotW, plotH);

        int n = data.size();
        // x labels
        int labels = Math.min(6, n);
        for (int i = 0; i < labels; i++) {
            int idx = labels == 1 ? 0 : (int) Math.round(i * (n - 1) / (double) (labels - 1));
            int x = xOf(idx, n, left, plotW);
            String s = data.get(idx).date().toString();
            g.setColor(Color.DARK_GRAY);
            g.drawString(s, x - fm.stringWidth(s) / 2, top + plotH + 22);
            g.drawLine(x, top + plotH, x, top + plotH + 4);
        }

        // buy / target lines
        float[] dash = {8f, 6f};
        for (ChartMarker m : markers) {
            int y = yOf(m.value(), lo, hi, top, plotH);
            Color c = m.kind() == ChartMarker.Kind.BUY ? new Color(230, 126, 34) : new Color(39, 150, 80);
            g.setColor(c);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g.drawLine(left, y, left + plotW, y);
            String s = String.format(Locale.US, "%s: %.2f", m.label(), m.value());
            g.drawString(s, left + plotW - fm.stringWidth(s) - 6, y - 5);
        }

        // price line
        Path2D.Double line = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double x = xOf(i, n, left, plotW);
            double y = yOf(data.get(i).close(), lo, hi, top, plotH);
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        g.setColor(new Color(33, 97, 190));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        // up/down dots for readability on shorter ranges
        if (n <= 120) {
            for (int i = 1; i < n; i++) {
                double a = data.get(i - 1).close(), b = data.get(i).close();
                g.setColor(b > a ? new Color(39, 150, 80) : b < a ? new Color(200, 50, 50) : Color.GRAY);
                int x = xOf(i, n, left, plotW);
                int y = yOf(b, lo, hi, top, plotH);
                g.fillOval(x - 3, y - 3, 6, 6);
            }
        }

        // legend
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(33, 97, 190));
        g.drawString("— close", left, h - 20);
        g.setColor(new Color(39, 150, 80));
        g.drawString("● up day", left + 80, h - 20);
        g.setColor(new Color(200, 50, 50));
        g.drawString("● down day", left + 160, h - 20);
        g.setColor(new Color(230, 126, 34));
        g.drawString("- - buying price", left + 250, h - 20);
        g.setColor(new Color(39, 150, 80));
        g.drawString("- - selling target", left + 370, h - 20);
        g.dispose();

        String safe = company.symbol().replaceAll("[^A-Za-z0-9._-]", "_");
        Path file = dir.resolve(safe + "_" + company.market() + ".png");
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    private static int xOf(int i, int n, int left, int plotW) {
        return n == 1 ? left + plotW / 2 : left + (int) Math.round(i * (double) plotW / (n - 1));
    }

    private static int yOf(double v, double lo, double hi, int top, int plotH) {
        return top + plotH - (int) Math.round((v - lo) / (hi - lo) * plotH);
    }
}
