package com.retailflow.config;

import com.retailflow.dto.UserCreateRequest;
import com.retailflow.entity.AppUser;
import com.retailflow.repository.UserRepository;
import com.retailflow.service.contract.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserService service;
  private final UserRepository repo;

  @Value("${BOOTSTRAP_ADMIN_USERNAME:}")
  private String username;

  @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
  private String email;

  @Value("${BOOTSTRAP_ADMIN_FULL_NAME:}")
  private String fullName;

  @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
  private String password;

  public AdminBootstrap(UserService service, UserRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank() || email.isBlank() || fullName.isBlank() || password.isBlank()) return;
    if (repo.count() > 0) return;
    if (password.length() < 12)
      throw new IllegalStateException("Bootstrap admin password must be at least 12 characters");
    AppUser u = service.create(new UserCreateRequest(username, email, fullName, password));
    u.role = "ADMIN";
    repo.save(u);
  }
}
