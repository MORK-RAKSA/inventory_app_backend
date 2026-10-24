package com.retailflow.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final JdbcTemplate db;
  private final SecureRandom random = new SecureRandom();
  private final String username;
  private final String password;
  private static final long ACCESS_SECONDS = 15 * 60;
  private static final long SESSION_SECONDS = 90L * 24 * 3600;

  public AuthController(
      JdbcTemplate db,
      @Value("${retailflow.auth.username:morkraksa}") String username,
      @Value("${retailflow.auth.password:admin1234}") String password) {
    this.db = db;
    this.username = username;
    this.password = password;
  }

  public record Credentials(String username, String password) {}

  public record RefreshRequest(String refreshToken) {}

  public record Tokens(String accessToken, String refreshToken, long expiresIn, String username) {}

  private String token() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session");
  }

  private Tokens newSession(Instant absoluteExpiry) {
    String access = token(), refresh = token();
    long now = Instant.now().getEpochSecond();
    db.update(
        "INSERT INTO auth_sessions(access_hash,refresh_hash,access_expires_at,session_expires_at) VALUES (?,?,?,?)",
        hash(access),
        hash(refresh),
        now + ACCESS_SECONDS,
        absoluteExpiry.getEpochSecond());
    return new Tokens(access, refresh, ACCESS_SECONDS, username);
  }

  @PostMapping("/login")
  public Tokens login(@RequestBody Credentials credentials) {
    if (credentials == null
        || credentials.username() == null
        || credentials.password() == null
        || !MessageDigest.isEqual(
            credentials.username().getBytes(StandardCharsets.UTF_8),
            username.getBytes(StandardCharsets.UTF_8))
        || !MessageDigest.isEqual(
            credentials.password().getBytes(StandardCharsets.UTF_8),
            password.getBytes(StandardCharsets.UTF_8))) throw unauthorized();
    return newSession(Instant.now().plusSeconds(SESSION_SECONDS));
  }

  @PostMapping("/refresh")
  public synchronized Tokens refresh(@RequestBody RefreshRequest request) {
    if (request == null || request.refreshToken() == null || request.refreshToken().isBlank())
      throw unauthorized();
    var rows =
        db.queryForList(
            "SELECT session_expires_at FROM auth_sessions WHERE refresh_hash=?",
            hash(request.refreshToken()));
    if (rows.isEmpty()) throw unauthorized();
    long expires = ((Number) rows.get(0).get("session_expires_at")).longValue();
    db.update("DELETE FROM auth_sessions WHERE refresh_hash=?", hash(request.refreshToken()));
    if (expires <= Instant.now().getEpochSecond()) throw unauthorized();
    return newSession(Instant.ofEpochSecond(expires));
  }

  @PostMapping("/logout")
  public Map<String, Boolean> logout(@RequestBody RefreshRequest request) {
    if (request != null && request.refreshToken() != null)
      db.update("DELETE FROM auth_sessions WHERE refresh_hash=?", hash(request.refreshToken()));
    return Map.of("success", true);
  }

  public boolean validAccess(String bearer) {
    if (bearer == null || !bearer.startsWith("Bearer ")) return false;
    String t = bearer.substring(7);
    if (t.isBlank()) return false;
    long now = Instant.now().getEpochSecond();
    return Boolean.TRUE.equals(
        db.query(
            "SELECT access_expires_at,session_expires_at FROM auth_sessions WHERE access_hash=?",
            rs -> rs.next() && rs.getLong(1) > now && rs.getLong(2) > now,
            hash(t)));
  }
}
