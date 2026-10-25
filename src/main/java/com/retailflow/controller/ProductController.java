package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import com.retailflow.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping
  public List<Product> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public Product get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/barcode/{value}")
  public Product barcode(@PathVariable String value) {
    return service.barcode(value);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Product create(@Valid @RequestBody ProductInput input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public Product edit(@PathVariable UUID id, @Valid @RequestBody ProductInput input) {
    return service.edit(id, input);
  }

  @PostMapping("/{id}/restock")
  public Product restock(@PathVariable UUID id, @Valid @RequestBody StockInput input) {
    return service.restock(id, input);
  }

  /** Archives the product. Returns 204 No Content (an empty 200 breaks JSON clients). */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(@PathVariable UUID id) {
    service.archive(id);
  }

  /**
   * multipart/form-data with a single "file" part. Responds 501 when R2 storage is not configured.
   */
  @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Product uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
    return service.setImage(id, file);
  }

  @DeleteMapping("/{id}/image")
  public Product removeImage(@PathVariable UUID id) {
    return service.removeImage(id);
  }
}
