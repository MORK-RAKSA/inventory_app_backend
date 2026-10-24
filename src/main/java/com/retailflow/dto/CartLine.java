package com.retailflow.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CartLine(@NotNull UUID productId, @Min(1) int quantity) {}
