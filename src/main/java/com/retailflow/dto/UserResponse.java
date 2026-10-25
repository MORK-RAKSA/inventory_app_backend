package com.retailflow.dto;

import com.retailflow.entity.AppUser;

public record UserResponse(
    String id, String username, String email, String fullName, String role, boolean active) {
  public static UserResponse from(AppUser u) {
    return new UserResponse(u.id, u.username, u.email, u.fullName, u.role, u.active);
  }
}
