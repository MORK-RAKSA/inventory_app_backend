package com.retailflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UomConfigInput(
    @NotBlank String name,
    String baseUnit,
    String caseUnitName,
    @Min(1) Integer caseQty,
    String description) {}
