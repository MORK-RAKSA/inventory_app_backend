package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
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
    if (input.usdToKhr() != null) {
      if (input.usdToKhr().signum() <= 0)
        throw new IllegalArgumentException("Exchange rate must be positive");
      s.usdToKhr = input.usdToKhr();
    }
    if (input.categories() != null) {
      s.setCategories(input.categories());
    }
    if (input.baseUnits() != null) {
      s.setBaseUnits(input.baseUnits());
    }
    if (input.currencyMode() != null) {
      if (!java.util.Set.of("live", "manual").contains(input.currencyMode()))
        throw new IllegalArgumentException("Invalid currency mode");
      s.currencyMode = input.currencyMode();
    }
    s.updatedAt = OffsetDateTime.now();
    return repo.save(s);
  }
}
