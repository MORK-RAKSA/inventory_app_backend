package com.retailflow.controller;

import com.retailflow.entity.AppUser;
import com.retailflow.service.contract.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final JdbcTemplate db;
  private final UserService users;
  private final SecureRandom random = new SecureRandom();

  @org.springframework.beans.factory.annotation.Value("${retail-flow.auth.recovery-key:}")
  private String recoveryKey;

  private static final long ACCESS_SECONDS = 86400, SESSION_SECONDS = 90L * 86400;

  public record Credentials(@NotBlank String username, @NotBlank String password) {}

  public record RefreshRequest(String refreshToken) {}

  public record Tokens(
      String accessToken,
      String refreshToken,
      long expiresIn,
      String username,
      String role,
      String fullName) {
    public Tokens(String accessToken, String refreshToken, long expiresIn, String username) {
      this(accessToken, refreshToken, expiresIn, username, "ADMIN", username);
    }
  }

  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session");
  }

  private String token() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Tokens newSession(AppUser u, Instant expiration) {
    String access = token(), refresh = token();
    long now = Instant.now().getEpochSecond();
    db.update(
        "INSERT INTO auth_sessions(access_hash,refresh_hash,access_expires_at,session_expires_at,user_id) VALUES (?,?,?,?,?)",
        hash(access),
        hash(refresh),
        now + ACCESS_SECONDS,
        expiration.getEpochSecond(),
        u.id);
    return new Tokens(
        access,
        refresh,
        ACCESS_SECONDS,
        u.username,
        u.role != null ? u.role : "ADMIN",
        u.fullName != null ? u.fullName : u.username);
  }

  @PostMapping("/login")
  public Tokens login(@Valid @RequestBody Credentials req) {
    AppUser u = users.authenticate(req.username(), req.password());
    return newSession(u, Instant.now().plusSeconds(SESSION_SECONDS));
  }

  @PostMapping("/refresh")
  public Tokens refresh(@RequestBody RefreshRequest req) {
    if (req == null || req.refreshToken() == null || req.refreshToken().isBlank())
      throw unauthorized();
    String hashed = hash(req.refreshToken());
    // Atomic rotation: only one request can consume each refresh token.
    return db.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Tokens>)
            conn -> {
              boolean previous = conn.getAutoCommit();
              conn.setAutoCommit(false);
              try (var select =
                  conn.prepareStatement(
                      "SELECT s.user_id,s.session_expires_at,u.username,u.email,u.full_name,u.password_hash,u.role,u.active FROM auth_sessions s JOIN app_users u ON u.id=s.user_id WHERE s.refresh_hash=? FOR UPDATE")) {
                select.setString(1, hashed);
                try (var rows = select.executeQuery()) {
                  if (!rows.next()
                      || !rows.getBoolean("active")
                      || rows.getLong("session_expires_at") <= Instant.now().getEpochSecond())
                    throw unauthorized();
                  AppUser u = new AppUser();
                  u.id = rows.getString("user_id");
                  u.username = rows.getString("username");
                  u.role = rows.getString("role");
                  u.fullName = rows.getString("full_name");
                  long expiry = rows.getLong("session_expires_at");
                  try (var delete =
                      conn.prepareStatement("DELETE FROM auth_sessions WHERE refresh_hash=?")) {
                    delete.setString(1, hashed);
                    delete.executeUpdate();
                  }
                  String access = token(), refresh = token();
                  try (var insert =
                      conn.prepareStatement(
                          "INSERT INTO auth_sessions(access_hash,refresh_hash,access_expires_at,session_expires_at,user_id) VALUES (?,?,?,?,?)")) {
                    insert.setString(1, hash(access));
                    insert.setString(2, hash(refresh));
                    insert.setLong(3, Instant.now().getEpochSecond() + ACCESS_SECONDS);
                    insert.setLong(4, expiry);
                    insert.setString(5, u.id);
                    insert.executeUpdate();
                  }
                  conn.commit();
                  return new Tokens(
                      access,
                      refresh,
                      ACCESS_SECONDS,
                      u.username,
                      u.role != null ? u.role : "ADMIN",
                      u.fullName != null ? u.fullName : u.username);
                }
              } catch (Exception e) {
                conn.rollback();
                throw e;
              } finally {
                conn.setAutoCommit(previous);
              }
            });
  }

  public record ResetPasswordRequest(
      @NotBlank String username, @NotBlank String newPassword, String recoveryKey) {}

  @PostMapping({"/reset-password", "/forgot-password"})
  public Map<String, Object> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    String key = req.recoveryKey() != null ? req.recoveryKey().trim() : "";
    // Disabled unless RECOVERY_KEY is configured; compared in constant time.
    if (recoveryKey == null
        || recoveryKey.isBlank()
        || !MessageDigest.isEqual(
            recoveryKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid recovery key");
    }
    AppUser u = users.resetPassword(req.username(), req.newPassword());
    return Map.of("success", true, "username", u.username);
  }

  @GetMapping("/me")
  public Map<String, Object> me(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    AppUser u = userForAccess(auth);
    if (u == null) throw unauthorized();
    return Map.of(
        "id",
        u.id,
        "username",
        u.username,
        "fullName",
        u.fullName != null ? u.fullName : u.username,
        "email",
        u.email != null ? u.email : "",
        "role",
        u.role != null ? u.role : "ADMIN",
        "active",
        u.active);
  }

  @PostMapping("/logout")
  public Map<String, Boolean> logout(@RequestBody RefreshRequest req) {
    if (req != null && req.refreshToken() != null)
      db.update("DELETE FROM auth_sessions WHERE refresh_hash=?", hash(req.refreshToken()));
    return Map.of("success", true);
  }

  public AppUser userForAccess(String bearer) {
    if (bearer == null || !bearer.startsWith("Bearer ")) return null;
    String raw = bearer.substring(7);
    if (raw.isBlank()) return null;
    long now = Instant.now().getEpochSecond();
    var list =
        db.query(
            "SELECT u.id,u.username,u.email,u.full_name,u.role,u.active FROM auth_sessions s JOIN app_users u ON u.id=s.user_id WHERE s.access_hash=? AND s.access_expires_at>? AND s.session_expires_at>? AND u.active=true",
            (rs, row) -> {
              AppUser u = new AppUser();
              u.id = rs.getString("id");
              u.username = rs.getString("username");
              u.email = rs.getString("email");
              u.fullName = rs.getString("full_name");
              u.role = rs.getString("role");
              u.active = rs.getBoolean("active");
              return u;
            },
            hash(raw),
            now,
            now);
    return list.isEmpty() ? null : list.get(0);
  }

  public boolean validAccess(String bearer) {
    return userForAccess(bearer) != null;
  }
}
