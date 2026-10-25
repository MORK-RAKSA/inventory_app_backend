package com.retailflow.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RateUpdate(@NotNull @DecimalMin("0.0001") BigDecimal usdToKhr) {}
