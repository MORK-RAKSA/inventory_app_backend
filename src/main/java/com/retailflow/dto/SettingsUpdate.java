package com.retailflow.dto;

import java.math.BigDecimal;
import java.util.List;

public record SettingsUpdate(
    BigDecimal usdToKhr, List<String> categories, List<String> baseUnits) {}
