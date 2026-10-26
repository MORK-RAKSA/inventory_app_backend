package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.exception.NotFoundException;
import com.retailflow.repository.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uom")
public class UomController {
  private final UomConfigRepository repo;
  private final ProductRepository productRepo;

  public UomController(UomConfigRepository repo, ProductRepository productRepo) {
    this.repo = repo;
    this.productRepo = productRepo;
  }

  @GetMapping
  public List<UomConfig> list() {
    return repo.findAllByOrderByNameAsc();
  }

  @PostMapping
  public UomConfig create(@Valid @RequestBody UomConfigInput in) {
    String id = "uom-" + UUID.randomUUID().toString().substring(0, 8);
    UomConfig cfg =
        new UomConfig(
            id,
            in.name().trim(),
            in.baseUnit() == null || in.baseUnit().isBlank() ? "Piece" : in.baseUnit().trim(),
            in.caseUnitName() == null || in.caseUnitName().isBlank()
                ? "Case"
                : in.caseUnitName().trim(),
            in.caseQty() == null || in.caseQty() < 1 ? 24 : in.caseQty(),
            in.description() == null ? "" : in.description().trim());
    return repo.save(cfg);
  }

  @PutMapping("/{id}")
  public UomConfig update(@PathVariable String id, @Valid @RequestBody UomConfigInput in) {
    UomConfig cfg =
        repo.findById(id).orElseThrow(() -> new NotFoundException("UOM config not found"));
    String oldCaseUnitName = cfg.caseUnitName;
    String oldBaseUnit = cfg.baseUnit;

    cfg.name = in.name().trim();
    if (in.baseUnit() != null && !in.baseUnit().isBlank()) cfg.baseUnit = in.baseUnit().trim();
    if (in.caseUnitName() != null && !in.caseUnitName().isBlank())
      cfg.caseUnitName = in.caseUnitName().trim();
    if (in.caseQty() != null && in.caseQty() > 0) cfg.caseQty = in.caseQty();
    if (in.description() != null) cfg.description = in.description().trim();
    UomConfig saved = repo.save(cfg);

    // Sync all existing products configured with this UOM in real-time
    List<Product> products = productRepo.findAll();
    for (Product p : products) {
      boolean matchCase = oldCaseUnitName != null && oldCaseUnitName.equalsIgnoreCase(p.caseUnitName);
      boolean matchBase = oldBaseUnit != null && oldBaseUnit.equalsIgnoreCase(p.baseUnit);
      if (matchCase || matchBase) {
        if (cfg.caseUnitName != null && !cfg.caseUnitName.isBlank()) p.caseUnitName = cfg.caseUnitName;
        if (cfg.baseUnit != null && !cfg.baseUnit.isBlank()) p.baseUnit = cfg.baseUnit;
        if (cfg.caseQty > 0) p.caseQty = cfg.caseQty;
        if (p.wholesalePriceUsd != null && p.caseQty > 0) {
          p.wholesalePricePerCaseUsd = p.wholesalePriceUsd.multiply(new java.math.BigDecimal(p.caseQty));
        }
        productRepo.save(p);
      }
    }

    return saved;
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    if (!repo.existsById(id)) throw new NotFoundException("UOM config not found");
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
