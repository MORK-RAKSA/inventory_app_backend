package com.retailflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_items")
public class SaleItem {
  @Id public UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sale_id")
  @com.fasterxml.jackson.annotation.JsonIgnore
  public Sale sale;

  @Column(name = "product_id")
  public UUID productId;

  @Column(name = "product_name")
  public String productName;

  public int quantity;

  @Column(name = "unit_price_usd")
  public BigDecimal unitPriceUsd;

  @Column(name = "line_total_usd")
  public BigDecimal lineTotalUsd;

  public int cases;
  public int loose;

  @Column(name = "case_unit_name")
  public String caseUnitName;

  @Column(name = "base_unit")
  public String baseUnit;

  public String uom;

  @Column(name = "is_wholesale")
  public boolean isWholesale;

  public SaleItem() {}
}
