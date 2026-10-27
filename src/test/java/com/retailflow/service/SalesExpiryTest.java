package com.retailflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SalesExpiryTest {
  @Test
  void expiredProductDoesNotChangeStockOrCreateSale() {
    var sales = mock(SaleRepository.class);
    var products = mock(ProductRepository.class);
    var settings = mock(SettingsRepository.class);
    var product = new Product();
    product.id = UUID.randomUUID();
    product.name = "Expired";
    product.stock = 5;
    product.expirationDate = LocalDate.now(ZoneId.of("Asia/Phnom_Penh")).minusDays(1);
    var config = new StoreSettings() {};
    config.usdToKhr = new BigDecimal("4100");
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    when(products.findLocked(product.id)).thenReturn(Optional.of(product));
    var service = new SalesService(sales, products, settings, mock(LiveEventService.class));
    var input =
        new CheckoutRequest(
            "expiry-test",
            "KHR",
            "RETAIL",
            null,
            null,
            null,
            null,
            null,
            List.of(new CartLine(product.id, 1, null, null, null, null, null, null)));
    assertThrows(IllegalArgumentException.class, () -> service.checkout(input));
    assertEquals(5, product.stock);
    verify(products, never()).save(any());
    verify(sales, never()).saveAndFlush(any());
  }
}
