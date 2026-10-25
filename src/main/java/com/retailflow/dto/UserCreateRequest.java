package com.retailflow.dto;

import jakarta.validation.constraints.*;

public record UserCreateRequest(
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Size(min = 6, max = 128) String password,
    String role) {
  public UserCreateRequest(String username, String email, String fullName, String password) {
    this(username, email, fullName, password, "ADMIN");
  }
}
