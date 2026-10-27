package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import com.retailflow.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {
  private final SettingsService service;

  @GetMapping
  public StoreSettings get() {
    return service.get();
  }

  @PutMapping("/rate")
  public StoreSettings rate(@Valid @RequestBody RateUpdate input) {
    return service.rate(input);
  }

  @PutMapping
  public StoreSettings update(@RequestBody SettingsUpdate input) {
    return service.update(input);
  }
}
