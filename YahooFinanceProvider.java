package com.stocktracker.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily candles from Yahoo Finance's public chart endpoint (no API key).
 * NSE tickers use the .NS suffix, BSE .BO, US tickers none.
 * This is an unofficial endpoint: if it ever changes, only this class needs to be replaced.
 */
public final class YahooFinanceProvider implements PriceProvider {

    private static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<DailyPrice> fetchDaily(Company company, String range) throws IOException, InterruptedException {
        String url = BASE + URLEncoder.encode(company.apiSymbol(), StandardCharsets.UTF_8)
                + "?range=" + range + "&interval=1d&includePrePost=false";

        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (StockTracker semester project)")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            if (sc == 200) return parse(company, res.body());
            if (sc == 404) throw new IOException("symbol not found at API (" + company.apiSymbol() + ")");
            if (sc == 429 || sc >= 500) {
                Thread.sleep(1500L * attempt);   // back off, then retry
                continue;
            }
            throw new IOException("HTTP " + sc + " for " + company.apiSymbol());
        }
        throw new IOException("API busy or rate limited for " + company.apiSymbol());
    }

    /** Package-private so it can be unit tested with a saved JSON sample. */
    static List<DailyPrice> parse(Company company, String json) throws IOException {
        JsonObject chart;
        try {
            chart = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("chart");
        } catch (RuntimeException e) {
            throw new IOException("unexpected API response");
        }
        if (chart == null) throw new IOException("unexpected API response");

        JsonElement err = chart.get("error");
        if (err != null && !err.isJsonNull()) {
            JsonObject eo = err.getAsJsonObject();
            String d = eo.has("description") && !eo.get("description").isJsonNull()
                    ? eo.get("description").getAsString() : "API error";
            throw new IOException(d);
        }

        JsonArray results = chart.getAsJsonArray("result");
        if (results == null || results.isEmpty()) throw new IOException("no data returned");
        JsonObject r = results.get(0).getAsJsonObject();

        long offset = 0;
        JsonObject meta = r.getAsJsonObject("meta");
        if (meta != null && meta.has("gmtoffset") && !meta.get("gmtoffset").isJsonNull()) {
            offset = meta.get("gmtoffset").getAsLong();
        }

        JsonArray ts = r.getAsJsonArray("timestamp");
        List<DailyPrice> out = new ArrayList<>();
        if (ts == null) return out;   // no trading days inside the requested range

        JsonObject quote = r.getAsJsonObject("indicators").getAsJsonArray("quote").get(0).getAsJsonObject();
        JsonArray open = quote.getAsJsonArray("open");
        JsonArray high = quote.getAsJsonArray("high");
        JsonArray low = quote.getAsJsonArray("low");
        JsonArray close = quote.getAsJsonArray("close");
        JsonArray vol = quote.getAsJsonArray("volume");

        for (int i = 0; i < ts.size(); i++) {
            if (isNull(close, i)) continue;   // holiday / no trade
            double c = close.get(i).getAsDouble();
            LocalDate date = Instant.ofEpochSecond(ts.get(i).getAsLong() + offset).atZone(ZoneOffset.UTC).toLocalDate();
            out.add(new DailyPrice(company.id(), date,
                    num(open, i, c), num(high, i, c), num(low, i, c), c,
                    (long) num(vol, i, 0)));
        }
        return out;
    }

    private static boolean isNull(JsonArray a, int i) {
        return a == null || i >= a.size() || a.get(i).isJsonNull();
    }

    private static double num(JsonArray a, int i, double def) {
        return isNull(a, i) ? def : a.get(i).getAsDouble();
    }
}
