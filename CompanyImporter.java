package com.stocktracker.util;

import com.stocktracker.db.CompanyDao;
import com.stocktracker.model.Company;
import com.stocktracker.model.Market;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads thousands of companies from a CSV (or '|' separated) file.
 * Understood layouts:
 * <ul>
 *   <li>our own: symbol,name,market</li>
 *   <li>NSE's EQUITY_L.csv: SYMBOL, NAME OF COMPANY, SERIES ... (only EQ/BE rows are kept)</li>
 *   <li>NASDAQ's nasdaqlisted.txt / otherlisted.txt (Symbol|Security Name|... test issues are skipped)</li>
 * </ul>
 * If the file has no 'market' column, the default market chosen by the caller is used.
 */
public final class CompanyImporter {

    private static final Set<String> SYMBOL_HEADERS = Set.of("symbol", "ticker", "nasdaq symbol", "act symbol", "cqs symbol");
    private static final Set<String> NAME_HEADERS = Set.of("name of company", "security name", "company name", "name", "company");

    public record Result(int rowsRead, int inserted) {
    }

    private CompanyImporter() {
    }

    public static Result importFile(Path file, Market defaultMarket, CompanyDao dao) throws IOException, SQLException {
        List<Company> parsed;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parsed = parse(r, defaultMarket);
        }
        int before = dao.count();
        dao.insertBatch(parsed);
        return new Result(parsed.size(), dao.count() - before);
    }

    public static List<Company> parse(Reader reader, Market defaultMarket) throws IOException {
        BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        List<Company> out = new ArrayList<>();

        String line;
        String headerLine = null;
        while ((line = br.readLine()) != null) {
            if (!line.isBlank()) {
                headerLine = line;
                break;
            }
        }
        if (headerLine == null) return out;
        if (headerLine.startsWith("﻿")) headerLine = headerLine.substring(1);

        char delim = headerLine.contains("|") ? '|' : ',';
        List<String> header = split(headerLine, delim);
        int symIdx = -1, nameIdx = -1, marketIdx = -1, seriesIdx = -1, testIdx = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (symIdx < 0 && SYMBOL_HEADERS.contains(h)) symIdx = i;
            else if (nameIdx < 0 && NAME_HEADERS.contains(h)) nameIdx = i;
            else if (h.equals("market") || h.equals("exchange")) marketIdx = i;
            else if (h.equals("series")) seriesIdx = i;
            else if (h.equals("test issue")) testIdx = i;
        }

        boolean hasHeader = symIdx >= 0;
        if (!hasHeader) {            // header-less file: symbol,name,market
            symIdx = 0;
            nameIdx = 1;
            marketIdx = 2;
            line = headerLine;
        } else {
            line = br.readLine();
        }

        for (; line != null; line = br.readLine()) {
            if (line.isBlank()) continue;
            List<String> f = split(line, delim);
            if (symIdx >= f.size()) continue;
            String symbol = f.get(symIdx).trim().toUpperCase(Locale.ROOT);
            if (symbol.isEmpty() || symbol.contains(" ") || symbol.startsWith("FILE CREATION")) continue;
            if (testIdx >= 0 && testIdx < f.size() && f.get(testIdx).trim().equalsIgnoreCase("Y")) continue;
            if (seriesIdx >= 0 && seriesIdx < f.size()) {
                String s = f.get(seriesIdx).trim();
                if (!s.equals("EQ") && !s.equals("BE")) continue;
            }
            String name = nameIdx >= 0 && nameIdx < f.size() ? f.get(nameIdx).trim() : symbol;
            if (name.isEmpty()) name = symbol;
            Market market = defaultMarket;
            if (marketIdx >= 0 && marketIdx < f.size()) {
                Market m = Market.tryParse(f.get(marketIdx));
                if (m != null) market = m;
            }
            out.add(new Company(0, Fmt.cut(symbol, 30), Fmt.cut(name, 200), market, true));
        }
        return out;
    }

    /** Minimal CSV splitter that understands double quotes. */
    static List<String> split(String line, char delim) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delim && !quoted) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return new ArrayList<>(Arrays.asList(parts.toArray(new String[0])));
    }
}
