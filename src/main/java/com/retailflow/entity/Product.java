package com.retailflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
  @Id public UUID id;
  public String name;

  public String barcode;

  public String category;

  @Column(name = "price_usd")
  public BigDecimal priceUsd;

  @Column(name = "original_price")
  public BigDecimal originalPrice;

  @Column(name = "original_ccy")
  public String originalCcy;

  @Column(name = "price_fx_rate")
  public BigDecimal priceFxRate;

  public int stock;

  @Column(name = "low_stock_threshold")
  public int lowStockThreshold;

  @Column(name = "image_url")
  public String imageUrl;

  @Column(name = "image_key")
  @com.fasterxml.jackson.annotation.JsonIgnore
  public String imageKey;

  @Column(name = "expiration_date")
  public java.time.LocalDate expirationDate;

  /**
   * Wholesale unit price in USD. Null = no wholesale price (wholesale checkout falls back to
   * retail).
   */
  @Column(name = "wholesale_price_usd")
  public BigDecimal wholesalePriceUsd;

  /** Wholesale case price in USD. */
  @Column(name = "wholesale_price_per_case_usd")
  public BigDecimal wholesalePricePerCaseUsd;

  /** Minimum quantity per line for the wholesale price to be recommended. */
  @Column(name = "wholesale_min_qty")
  public int wholesaleMinQty = 1;

  @Column(name = "base_unit")
  public String baseUnit = "Piece";

  @Column(name = "case_unit_name")
  public String caseUnitName = "Case";

  @Column(name = "case_qty")
  public int caseQty = 24;

  public boolean archived;
  @Version public long version;

  @Column(name = "created_at", insertable = false, updatable = false)
  public java.time.OffsetDateTime createdAt;

  public Product() {}
}
