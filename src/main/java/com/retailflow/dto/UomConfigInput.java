package com.retailflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UomConfigInput(
    @NotBlank String name,
    @NotBlank String baseUnit,
    @NotBlank String caseUnitName,
    @NotNull @Min(1) Integer caseQty,
    String description) {}
