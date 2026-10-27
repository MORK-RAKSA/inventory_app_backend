package com.retailflow.controller;

import com.retailflow.entity.AppUser;
import com.retailflow.service.PayWaySandboxService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments/sandbox")
@RequiredArgsConstructor
public class PayWaySandboxController {
  private final PayWaySandboxService service;

  public record Request(
      @NotNull UUID requestId,
      @NotNull @DecimalMin("0.01") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
          BigDecimal amount) {}

  private void admin(HttpServletRequest request) {
    AppUser user = (AppUser) request.getAttribute("currentUser");
    if (user == null || !"ADMIN".equals(user.role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator required");
  }

  @GetMapping("/config")
  public Map<String, Object> config(HttpServletRequest request) {
    admin(request);
    return Map.of("enabled", service.available(), "sandbox", true);
  }

  @PostMapping
  public PayWaySandboxService.Payment create(
      @Valid @RequestBody Request body, HttpServletRequest request) {
    admin(request);
    return service.create(body.requestId().toString(), body.amount());
  }

  @PostMapping("/{id}/check")
  public PayWaySandboxService.Payment check(@PathVariable String id, HttpServletRequest request) {
    admin(request);
    return service.check(id);
  }
}
