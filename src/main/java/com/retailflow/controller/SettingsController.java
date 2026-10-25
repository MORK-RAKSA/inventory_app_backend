package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import com.retailflow.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
  private final SettingsService service;

  public SettingsController(SettingsService service) {
    this.service = service;
  }

  @GetMapping
  public StoreSettings get() {
    return service.get();
  }

  @PutMapping("/rate")
  public StoreSettings rate(@Valid @RequestBody RateUpdate input) {
    return service.rate(input);
  }
}
