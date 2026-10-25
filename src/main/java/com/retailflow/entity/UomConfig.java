package com.retailflow.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "uom_configs")
public class UomConfig {
  @Id public String id;
  public String name;

  @Column(name = "base_unit")
  public String baseUnit = "Piece";

  @Column(name = "case_unit_name")
  public String caseUnitName = "Case";

  @Column(name = "case_qty")
  public int caseQty = 24;

  public String description;

  @Column(name = "created_at", insertable = false, updatable = false)
  public OffsetDateTime createdAt;

  public UomConfig() {}

  public UomConfig(
      String id,
      String name,
      String baseUnit,
      String caseUnitName,
      int caseQty,
      String description) {
    this.id = id;
    this.name = name;
    this.baseUnit = baseUnit;
    this.caseUnitName = caseUnitName;
    this.caseQty = caseQty;
    this.description = description;
  }
}
