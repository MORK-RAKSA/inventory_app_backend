package com.retailflow.controller;

import com.retailflow.entity.Product;
import com.retailflow.repository.ProductRepository;
import com.retailflow.service.LiveEventService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {
  private final LiveEventService events;
  private final ProductRepository products;

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "UP",
        "database", "H2",
        "app", "RetailFlow Spring Boot Backend",
        "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
  }

  @GetMapping("/events")
  public List<LiveEventService.LiveEvent> getEvents(@RequestParam(defaultValue = "0") long since) {
    return events.since(since);
  }

  /**
   * Wireless mobile scanner gun endpoint. The phone beams a barcode plus the selected function so
   * the web portal can route itself (POS, inventory find, product form, batch intake).
   */
  @PostMapping("/events/scan")
  public ResponseEntity<Map<String, Object>> beamScan(
      @RequestBody(required = false) Map<String, Object> body) {
    Object rawBarcode = body == null ? null : body.get("barcode");
    String barcode = rawBarcode == null ? "" : String.valueOf(rawBarcode).trim();
    if (barcode.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Missing barcode"));
    }
    Object rawMode = body.get("scanMode");
    String scanMode = rawMode == null ? "checkout" : String.valueOf(rawMode);
    LiveEventService.LiveEvent ev = events.pushScan(barcode, scanMode);
    return ResponseEntity.ok(Map.of("ok", true, "event", ev));
  }

  @PostMapping("/state/sync")
  public Map<String, Object> syncState() {
    // H2 is a persistent relational database on disk, so no cold-start sync is required.
    return Map.of("status", "PERSISTED_IN_POSTGRESQL", "updatedAt", System.currentTimeMillis());
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
      res.put("source", "database");
      return ResponseEntity.ok(res);
    }
    return ResponseEntity.status(404).body(Map.of("error", "Barcode not found"));
  }
}
