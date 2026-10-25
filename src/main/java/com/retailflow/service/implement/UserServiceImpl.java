package com.retailflow.service.implement;

import com.retailflow.dto.UserCreateRequest;
import com.retailflow.entity.AppUser;
import com.retailflow.repository.UserRepository;
import com.retailflow.service.contract.UserService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserServiceImpl implements UserService {
  private final UserRepository repository;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  public UserServiceImpl(UserRepository repository) {
    this.repository = repository;
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
    user.passwordHash = passwords.encode(req.password());
    user.createdAt = LocalDateTime.now();
    user.updatedAt = user.createdAt;
    return repository.save(user);
  }

  @Override
  @Transactional(readOnly = true)
  public AppUser authenticate(String username, String password) {
    AppUser u =
        repository.findByUsernameIgnoreCase(username == null ? "" : username.trim()).orElse(null);
    if (u == null || !u.active || password == null || !passwords.matches(password, u.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    return u;
  }
}
