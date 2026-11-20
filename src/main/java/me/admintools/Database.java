package me.admintools;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {
    public interface Mapper<T> { T map(ResultSet rs) throws SQLException; }

    private final Connection con;

    public Database(File file) throws Exception {
        Class.forName("org.sqlite.JDBC");
        con = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS punishments(id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT, name TEXT, type TEXT, reason TEXT, staff TEXT, created INTEGER, expires INTEGER, active INTEGER DEFAULT 1)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pun ON punishments(uuid, type)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS blocklog(id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, uuid TEXT, name TEXT, action TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER, material TEXT)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bl ON blocklog(world, x, z)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS freezes(uuid TEXT, type TEXT, PRIMARY KEY(uuid, type))");
        }
    }

    public synchronized void update(String sql, Object... args) {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, args);
            ps.executeUpdate();
        } catch (SQLException e) {
            Logger.getLogger("AdminTools").log(Level.SEVERE, "SQL error", e);
        }
    }

    public synchronized <T> List<T> query(String sql, Mapper<T> mapper, Object... args) {
        List<T> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapper.map(rs));
            }
        } catch (SQLException e) {
            Logger.getLogger("AdminTools").log(Level.SEVERE, "SQL error", e);
        }
        return out;
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
    }

    public synchronized void close() {
        try { con.close(); } catch (SQLException ignored) {}
    }
}
