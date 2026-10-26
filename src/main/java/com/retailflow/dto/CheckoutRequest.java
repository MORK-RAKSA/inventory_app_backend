package com.retailflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String paidCurrency,
    /** "RETAIL" (default) or "WHOLESALE". */
    String priceTier,
    BigDecimal tenderAmount,
    String tenderCurrency,
    String changeText,
    BigDecimal changeKhr,
    BigDecimal changeUsd,
    @NotEmpty List<@Valid CartLine> items) {}
