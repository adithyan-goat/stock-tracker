package com.stocktracker.db;

import com.stocktracker.model.Alert;
import com.stocktracker.model.AlertType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AlertDao {

    private static Alert map(ResultSet rs) throws SQLException {
        return new Alert(rs.getLong("id"), rs.getLong("holding_id"), rs.getLong("company_id"),
                AlertType.valueOf(rs.getString("alert_type")), rs.getDate("trade_date").toLocalDate(),
                rs.getString("message"), rs.getObject("created_at", java.time.LocalDateTime.class),
                rs.getBoolean("is_read"));
    }

    /** Returns false when the same alert (lot + type + day) was already stored. */
    public boolean insertIfNew(Alert a) throws SQLException {
        String sql = "INSERT IGNORE INTO alerts (holding_id, company_id, alert_type, trade_date, message) VALUES (?,?,?,?,?)";
        String msg = a.message().length() > 1000 ? a.message().substring(0, 1000) : a.message();
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, a.holdingId());
            ps.setLong(2, a.companyId());
            ps.setString(3, a.type().name());
            ps.setDate(4, Date.valueOf(a.tradeDate()));
            ps.setString(5, msg);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Alert> findUnread() throws SQLException {
        return query("SELECT * FROM alerts WHERE is_read=FALSE ORDER BY trade_date DESC, id DESC", 0);
    }

    public List<Alert> findRecent(int limit) throws SQLException {
        return query("SELECT * FROM alerts ORDER BY trade_date DESC, id DESC LIMIT ?", limit);
    }

    private List<Alert> query(String sql, int limit) throws SQLException {
        List<Alert> out = new ArrayList<>();
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (limit > 0) ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public void markAllRead() throws SQLException {
        try (Connection con = Database.get(); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE alerts SET is_read=TRUE WHERE is_read=FALSE");
        }
    }
}
