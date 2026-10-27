package com.retailflow.service.implement;

import com.retailflow.dto.UserCreateRequest;
import com.retailflow.entity.AppUser;
import com.retailflow.repository.UserRepository;
import com.retailflow.service.contract.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
  private final UserRepository repository;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  private static String sha256(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      return s;
    }
  }

  @Override
  @Transactional
  public AppUser create(UserCreateRequest req) {
    if (repository.existsByUsernameIgnoreCase(req.username().trim())
        || repository.existsByEmailIgnoreCase(req.email().trim()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Username or email is already registered");
    AppUser user = new AppUser();
    user.id = UUID.randomUUID().toString();
    user.username = req.username().trim();
    user.email = req.email().trim().toLowerCase(java.util.Locale.ROOT);
    user.fullName = req.fullName().trim();
    user.role =
        req.role() != null && !req.role().isBlank() ? req.role().trim().toUpperCase() : "ADMIN";
    user.passwordHash = passwords.encode(req.password());
    user.active = true;
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    return repository.save(user);
  }

  @Override
  @Transactional
  public AppUser authenticate(String username, String password) {
    if (username == null || username.isBlank() || password == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    String uName = username.trim();
    AppUser u = repository.findByUsernameIgnoreCase(uName).orElse(null);

    if (u == null || !u.active) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    boolean matches = false;
    String stored = u.passwordHash != null ? u.passwordHash : "";

    if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
      matches = passwords.matches(password, stored);
    } else {
      // Plain text or SHA-256 match for easy manual DB setup/queries
      matches = password.equals(stored) || sha256(password).equalsIgnoreCase(stored);
      // Auto-upgrade plain text/SHA-256 to secure BCrypt hash upon successful login
      if (matches) {
        u.passwordHash = passwords.encode(password);
        u.updatedAt = LocalDateTime.now();
        repository.save(u);
      }
    }

    if (!matches) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    return u;
  }

  @Override
  @Transactional
  public AppUser resetPassword(String username, String newPassword) {
    if (username == null || username.isBlank() || newPassword == null || newPassword.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Username and new password required");
    }
    AppUser u =
        repository
            .findByUsernameIgnoreCase(username.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    u.passwordHash = passwords.encode(newPassword.trim());
    u.updatedAt = LocalDateTime.now();
    return repository.save(u);
  }
}
