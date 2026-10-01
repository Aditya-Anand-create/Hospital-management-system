package hms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens JDBC connections. The URL, user and password come from environment variables,
 * so no password is written in the code.
 */
public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Reads DB_URL, DB_USER, DB_PASSWORD (with local MySQL defaults, except the password). */
    public static Database fromEnvironment() {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/hospital_db");
        String user = System.getenv().getOrDefault("DB_USER", "root");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        return new Database(url, user, password);
    }

    /** Every call gives a new connection, so several admins can work at the same time. */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Creates the tables from schema.sql if they are missing (used by tests and first run). */
    public void createTables() {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new HmsException("Could not read schema.sql", e);
        }
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String part : sql.split(";")) {
                // Drop "-- comment" lines, then skip empty pieces and the CREATE DATABASE/USE lines
                // (the connection URL already points at the database).
                StringBuilder clean = new StringBuilder();
                for (String line : part.split("\n")) {
                    int cut = line.indexOf("--");
                    clean.append(cut >= 0 ? line.substring(0, cut) : line).append('\n');
                }
                String stmt = clean.toString().trim();
                if (stmt.isEmpty()) continue;
                String lower = stmt.toLowerCase();
                if (lower.startsWith("create database") || lower.startsWith("use ")) continue;
                st.execute(stmt);
            }
        } catch (SQLException e) {
            throw new HmsException("Could not create tables: " + e.getMessage(), e);
        }
    }

    /** True if the SQL error is a "constraint broken" error (duplicate, bad reference...). */
    static boolean isConstraintError(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }
}
