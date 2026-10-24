package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final ProductService service;

  public InventoryController(ProductService service) {
    this.service = service;
  }

  /**
   * All-or-nothing batch save: restock existing products and create new ones in one transaction.
   */
  @PostMapping("/batches/commit")
  public BatchResult commit(@Valid @RequestBody BatchCommitRequest input) {
    return service.commitBatch(input);
  }
}
