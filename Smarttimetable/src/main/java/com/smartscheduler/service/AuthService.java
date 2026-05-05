package com.smartscheduler.service;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Handles registration, login, and password verification.
 *
 * Passwords are stored as {@code salt:sha256(salt + password)} (Base64). This
 * is intentionally simple — for a real product, prefer BCrypt / Argon2.
 */
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();

    public static class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
        public AuthException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Register a new user. Returns the persisted User (with id). */
    public User register(String username, String rawPassword) throws AuthException {
        if (username == null || username.trim().length() < 3) {
            throw new AuthException("Username must be at least 3 characters");
        }
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new AuthException("Password must be at least 4 characters");
        }

        String hash = hashWithNewSalt(rawPassword);

        String sql = "INSERT INTO users(username, password) VALUES (?, ?)";
        try (Connection c = DatabaseConnection.get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return new User(keys.getInt(1), username.trim(), hash, LocalDateTime.now());
                }
            }
            throw new AuthException("Registration failed: no id generated");
        } catch (SQLIntegrityConstraintViolationException dup) {
            throw new AuthException("Username already taken");
        } catch (SQLException e) {
            throw new AuthException("Database error during registration", e);
        }
    }

    /** Verify credentials. Returns the User on success, throws on failure. */
    public User login(String username, String rawPassword) throws AuthException {
        String sql = "SELECT user_id, username, password, created_at FROM users WHERE username = ?";
        try (Connection c = DatabaseConnection.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException("Invalid username or password");
                }
                String stored = rs.getString("password");
                if (!verify(rawPassword, stored)) {
                    throw new AuthException("Invalid username or password");
                }
                Timestamp ts = rs.getTimestamp("created_at");
                return new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        stored,
                        ts == null ? null : ts.toLocalDateTime());
            }
        } catch (SQLException e) {
            throw new AuthException("Database error during login", e);
        }
    }

    // ------------------------------------------------------------------
    // hashing helpers
    // ------------------------------------------------------------------
    private static String hashWithNewSalt(String raw) throws AuthException {
        byte[] saltBytes = new byte[16];
        RNG.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        return salt + ":" + sha256(salt + raw);
    }

    private static boolean verify(String raw, String stored) throws AuthException {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        return sha256(parts[0] + raw).equals(parts[1]);
    }

    private static String sha256(String s) throws AuthException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (NoSuchAlgorithmException e) {
            throw new AuthException("SHA-256 unavailable", e);
        }
    }
}
