package com.retailflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "sales")
public class Sale {
  @Id public UUID id;

  @Column(name = "idempotency_key", unique = true)
  public String idempotencyKey;

  @Column(name = "created_at", insertable = false, updatable = false)
  public OffsetDateTime createdAt;

  @Column(name = "rate_snapshot")
  public BigDecimal rateSnapshot;

  @Column(name = "total_usd")
  public BigDecimal totalUsd;

  @Column(name = "total_khr")
  public BigDecimal totalKhr;

  @Column(name = "paid_currency")
  public String paidCurrency;

  @Column(name = "tender_amount")
  public BigDecimal tenderAmount;

  @Column(name = "tender_currency")
  public String tenderCurrency;

  @Column(name = "change_text")
  public String changeText;

  @Column(name = "change_khr")
  public BigDecimal changeKhr;

  @Column(name = "change_usd")
  public BigDecimal changeUsd;

  @Column(name = "cashier_name")
  public String cashierName;

  public String status;

  /** RETAIL or WHOLESALE — which price list this sale was charged at. */
  @Column(name = "price_tier")
  public String priceTier = "RETAIL";

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @OrderBy("productName asc")
  public List<SaleItem> items = new ArrayList<>();

  public Sale() {}
}
