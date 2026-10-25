package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class SettingsService {
  private final SettingsRepository repo;

  public SettingsService(SettingsRepository repo) {
    this.repo = repo;
  }

  public StoreSettings get() {
    return repo.findById(1L).orElseThrow();
  }

  @Transactional
  public StoreSettings rate(RateUpdate input) {
    StoreSettings s = get();
    s.usdToKhr = input.usdToKhr();
    s.updatedAt = OffsetDateTime.now();
    return repo.save(s);
  }
}
