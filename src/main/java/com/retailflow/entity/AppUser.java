package com.retailflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 100, unique = true)
  public String username;

  @Column(nullable = false, length = 254, unique = true)
  public String email;

  @Column(name = "full_name", nullable = false, length = 200)
  public String fullName;

  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(nullable = false, length = 30)
  public String role = "USER";

  @Column(nullable = false)
  public boolean active = true;

  @Column(name = "created_at", nullable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  public LocalDateTime updatedAt;
}
