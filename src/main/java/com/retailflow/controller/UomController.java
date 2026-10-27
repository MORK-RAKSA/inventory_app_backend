package com.retailflow.controller;

import com.retailflow.dto.UomConfigInput;
import com.retailflow.entity.UomConfig;
import com.retailflow.service.UomService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uom")
@RequiredArgsConstructor
public class UomController {
  private final UomService service;

  @GetMapping
  public List<UomConfig> list() {
    return service.list();
  }

  @PostMapping
  public UomConfig create(@Valid @RequestBody UomConfigInput input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public UomConfig update(@PathVariable String id, @Valid @RequestBody UomConfigInput input) {
    return service.update(id, input);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
