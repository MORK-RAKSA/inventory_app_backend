package com.retailflow.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a batch. Either restock an existing product ({@code productId} + {@code
 * quantityAdded}) or create a new one ({@code name}, {@code category}, {@code price}, ...).
 */
public record BatchItem(
    UUID productId,
    Integer quantityAdded,
    String name,
    String barcode,
    String category,
    BigDecimal price,
    String currency,
    Integer stock,
    Integer lowStockThreshold) {}
