package com.retailflow.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class MoneyUtil {
  private static final Set<String> CURRENCIES = Set.of("USD", "KHR");

  private MoneyUtil() {}

  public static String requireCurrency(String currency) {
    if (!CURRENCIES.contains(currency))
      throw new IllegalArgumentException("Currency must be USD or KHR");
    return currency;
  }

  public static BigDecimal toUsd(BigDecimal amount, String currency, BigDecimal usdToKhr) {
    requireCurrency(currency);
    if (usdToKhr == null || usdToKhr.signum() <= 0)
      throw new IllegalArgumentException("Invalid exchange rate");
    return "USD".equals(currency) ? amount : amount.divide(usdToKhr, 4, RoundingMode.HALF_UP);
  }

  public static BigDecimal toKhr(BigDecimal usdAmount, BigDecimal rate) {
    BigDecimal raw = usdAmount.multiply(rate);
    if (raw.signum() <= 0) return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    BigDecimal hundreds = raw.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    if (hundreds.signum() == 0) hundreds = BigDecimal.ONE;
    return hundreds.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY);
  }
}
