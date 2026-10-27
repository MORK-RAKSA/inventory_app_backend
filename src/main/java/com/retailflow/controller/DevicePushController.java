package com.retailflow.controller;

import com.retailflow.entity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/push")
@RequiredArgsConstructor
public class DevicePushController {
  private final JdbcTemplate db;

  public record Registration(
      @Pattern(regexp = "(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]+\\]") @NotBlank
          String token,
      @NotBlank @Size(max = 100) String clientId) {}

  @PostMapping
  public void register(@Valid @RequestBody Registration body, HttpServletRequest request) {
    AppUser user = (AppUser) request.getAttribute("currentUser");
    db.update("DELETE FROM device_push_tokens WHERE token=?", body.token());
    db.update(
        "INSERT INTO device_push_tokens(token,user_id,client_id) VALUES(?,?,?)",
        body.token(),
        user.id,
        body.clientId());
  }
}
