package com.smartscheduler.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Loads JDBC settings from {@code db.properties} on the classpath and hands out
 * java.sql.Connection objects on demand. Each call returns a fresh connection;
 * callers are expected to close it (try-with-resources).
 */
public final class DatabaseConnection {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = DatabaseConnection.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("db.properties not found on classpath");
            }
            PROPS.load(in);
            Class.forName(PROPS.getProperty("db.driver"));
            bootstrapSchema();
        } catch (IOException | ClassNotFoundException | SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConnection() {
        /* utility */ }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(
                PROPS.getProperty("db.url"),
                PROPS.getProperty("db.user"),
                PROPS.getProperty("db.password"));
    }

    public static String prop(String key, String fallback) {
        return PROPS.getProperty(key, fallback);
    }

    private static void bootstrapSchema() throws SQLException, IOException {
        String url = PROPS.getProperty("db.url");
        String user = PROPS.getProperty("db.user");
        String password = PROPS.getProperty("db.password");

        String databaseName = extractDatabaseName(url);
        String serverUrl = stripDatabaseFromUrl(url);

        try (Connection c = DriverManager.getConnection(serverUrl, user, password); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName
                    + "` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");
        }

        String schemaSql = loadSchemaSql();
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            for (String statement : schemaSql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        s.execute(trimmed);
                    } catch (SQLSyntaxErrorException ex) {
                        String msg = ex.getMessage();
                        if (msg != null && msg.toLowerCase().contains("duplicate key name")) {
                            continue;
                        }
                        throw ex;
                    }
                }
            }
        }
    }

    private static String loadSchemaSql() throws IOException {
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        for (Path candidate : new Path[] {
                Paths.get("schema.sql"),
                Paths.get("src", "main", "resources", "schema.sql")
        }) {
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }

        throw new IOException("schema.sql not found on classpath or in the project directory");
    }

    private static String extractDatabaseName(String url) {
        int dbStart = url.indexOf("//");
        if (dbStart >= 0) {
            int slashAfterHost = url.indexOf('/', dbStart + 2);
            if (slashAfterHost >= 0) {
                int queryStart = url.indexOf('?', slashAfterHost);
                String database = queryStart >= 0
                        ? url.substring(slashAfterHost + 1, queryStart)
                        : url.substring(slashAfterHost + 1);
                if (!database.isBlank()) {
                    return database;
                }
            }
        }
        throw new IllegalStateException("db.url must include a database name");
    }

    private static String stripDatabaseFromUrl(String url) {
        int dbStart = url.indexOf("//");
        if (dbStart >= 0) {
            int slashAfterHost = url.indexOf('/', dbStart + 2);
            if (slashAfterHost >= 0) {
                int queryStart = url.indexOf('?', slashAfterHost);
                String prefix = url.substring(0, slashAfterHost + 1);
                String suffix = queryStart >= 0 ? url.substring(queryStart) : "";
                return prefix + suffix;
            }
        }
        return url;
    }
}
