package com.stocktracker.db;

import com.stocktracker.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens MySQL connections and creates the tables on first start. */
public final class Database {

    private static String url;
    private static String user;
    private static String password;

    private Database() {
    }

    public static void init(Config cfg) throws SQLException {
        url = cfg.dbUrl();
        user = cfg.dbUser();
        password = cfg.dbPassword();
        createSchema();
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void createSchema() throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new SQLException("schema.sql not found on the classpath");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Cannot read schema.sql", e);
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("--")) continue;
            int c = line.indexOf("--");
            cleaned.append(c >= 0 ? line.substring(0, c) : line).append('\n');
        }

        try (Connection con = get(); Statement st = con.createStatement()) {
            for (String sql : cleaned.toString().split(";")) {
                if (!sql.isBlank()) st.execute(sql);
            }
        }
    }
}
