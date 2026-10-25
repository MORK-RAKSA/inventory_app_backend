package com.retailflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CheckoutRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String paidCurrency,
    /** "RETAIL" (default) or "WHOLESALE". */
    String priceTier,
    @NotEmpty List<@Valid CartLine> items) {}
