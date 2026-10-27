package com.retailflow.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductInput(
    @NotBlank String name,
    String barcode,
    @NotBlank String category,
    @NotNull @DecimalMin("0") BigDecimal price,
    @NotBlank String currency,
    @Min(0) int stock,
    @Min(0) int lowStockThreshold,
    java.time.LocalDate expirationDate,
    /** Optional wholesale price, expressed in the same {@code currency} as {@code price}. */
    @DecimalMin("0") BigDecimal wholesalePrice,
    BigDecimal wholesalePricePerCase,
    BigDecimal wholesalePriceUsd,
    BigDecimal wholesalePricePerCaseUsd,
    @Min(1) Integer wholesaleMinQty,
    String baseUnit,
    String caseUnitName,
    @Min(1) Integer caseQty) {}
