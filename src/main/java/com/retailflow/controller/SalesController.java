package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import com.retailflow.service.*;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SalesController {
  private final SalesService service;

  public SalesController(SalesService service) {
    this.service = service;
  }

  @GetMapping
  public List<Sale> list(
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return service.list(from, to);
  }

  @GetMapping("/{id}")
  public Sale get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/checkout")
  public Sale checkout(@Valid @RequestBody CheckoutRequest input) {
    return service.checkout(input);
  }

  @DeleteMapping("/{id}")
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
