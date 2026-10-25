package com.retailflow.dto;

import jakarta.validation.constraints.Min;

public record StockInput(@Min(1) int quantity) {}
