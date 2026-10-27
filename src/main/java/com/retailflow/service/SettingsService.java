package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {
  private final SettingsRepository repo;

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

  @Transactional
  public StoreSettings update(SettingsUpdate input) {
    StoreSettings s = get();
    if (input.usdToKhr() != null && input.usdToKhr().compareTo(BigDecimal.ZERO) > 0) {
      s.usdToKhr = input.usdToKhr();
    }
    if (input.categories() != null) {
      s.setCategories(input.categories());
    }
    if (input.baseUnits() != null) {
      s.setBaseUnits(input.baseUnits());
    }
    s.updatedAt = OffsetDateTime.now();
    return repo.save(s);
  }
}
