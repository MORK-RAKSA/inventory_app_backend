package com.retailflow.product;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
  @Id public UUID id;

  @Column(name = "product_id")
  public UUID productId;

  @Column(name = "quantity_delta")
  public int quantityDelta;

  public String reason;

  @Column(name = "reference_id")
  public UUID referenceId;

  @Column(name = "created_at", insertable = false, updatable = false)
  public java.time.OffsetDateTime createdAt;

  public StockMovement() {}

  public StockMovement(UUID productId, int quantityDelta, String reason, UUID referenceId) {
    this.id = UUID.randomUUID();
    this.productId = productId;
    this.quantityDelta = quantityDelta;
    this.reason = reason;
    this.referenceId = referenceId;
  }
}
