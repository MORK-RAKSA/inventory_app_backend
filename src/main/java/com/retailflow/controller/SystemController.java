package com.retailflow.controller;

import com.retailflow.product.Product;
import com.retailflow.product.ProductRepository;
import com.retailflow.service.LiveEventService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SystemController {
  private final LiveEventService events;
  private final ProductRepository products;

  public SystemController(LiveEventService events, ProductRepository products) {
    this.events = events;
    this.products = products;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "UP",
        "database", "H2 SQL (./data/retailflow)",
        "app", "គ្រប់ហាង · EasySell Spring Boot + H2 Backend",
        "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
  }

  @GetMapping("/events")
  public List<LiveEventService.LiveEvent> getEvents(
      @RequestParam(defaultValue = "0") long since) {
    return events.since(since);
  }

  @PostMapping("/state/sync")
  public Map<String, Object> syncState() {
    // H2 is a persistent relational database on disk, so no cold-start sync is required.
    return Map.of("status", "PERSISTED_IN_H2", "updatedAt", System.currentTimeMillis());
  }

  @GetMapping("/barcode-lookup/{code}")
  public ResponseEntity<Map<String, Object>> lookupBarcode(@PathVariable String code) {
    String clean = code == null ? "" : code.trim();
    if (clean.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Barcode is required"));
    }
    Optional<Product> existing = products.findFirstByBarcode(clean);
    if (existing.isPresent()) {
      Product p = existing.get();
      Map<String, Object> res = new HashMap<>();
      res.put("barcode", clean);
      res.put("name", p.name);
      res.put("category", p.category);
      res.put("imageUrl", p.imageUrl);
      res.put("source", "h2");
      return ResponseEntity.ok(res);
    }
    return ResponseEntity.status(404).body(Map.of("error", "Not in local H2 database"));
  }
}
