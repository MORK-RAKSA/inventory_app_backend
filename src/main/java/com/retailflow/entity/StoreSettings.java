package com.retailflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "store_settings")
public class StoreSettings {
  @Id public Long id;

  @Column(name = "usd_to_khr")
  public BigDecimal usdToKhr;

  @Column(name = "updated_at")
  public java.time.OffsetDateTime updatedAt;

  protected StoreSettings() {}
}
