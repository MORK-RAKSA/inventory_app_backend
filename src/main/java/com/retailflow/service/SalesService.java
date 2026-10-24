package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.product.*;
import com.retailflow.sales.*;
import com.retailflow.settings.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class SalesService {
  private final SaleRepository sales;
  private final ProductRepository products;
  private final SettingsRepository settings;

  public SalesService(
      SaleRepository sales, ProductRepository products, SettingsRepository settings) {
    this.sales = sales;
    this.products = products;
    this.settings = settings;
  }

  public List<Sale> list(OffsetDateTime from, OffsetDateTime to) {
    OffsetDateTime start = from == null ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(30) : from;
    OffsetDateTime end = to == null ? OffsetDateTime.now(ZoneOffset.UTC).plusDays(1) : to;
    if (!end.isAfter(start)) throw new IllegalArgumentException("Invalid date range");
    return sales.byDate(start, end);
  }

  public Sale get(UUID id) {
    return sales.findById(id).orElseThrow(() -> new IllegalArgumentException("Sale not found"));
  }

  @Transactional
  public Sale checkout(CheckoutRequest in) {
    com.retailflow.util.MoneyUtil.requireCurrency(in.paidCurrency());
    Optional<Sale> existing = sales.findByIdempotencyKey(in.idempotencyKey());
    if (existing.isPresent()) return existing.get();
    Map<UUID, Integer> merged = new TreeMap<>();
    for (CartLine line : in.items())
      merged.merge(line.productId(), line.quantity(), Math::addExact);
    // Lock rows deterministically to avoid oversells/deadlocks between cashiers.
    BigDecimal rate = settings.findById(1L).orElseThrow().usdToKhr;
    Sale s = new Sale();
    s.id = UUID.randomUUID();
    s.idempotencyKey = in.idempotencyKey();
    s.paidCurrency = in.paidCurrency();
    s.rateSnapshot = rate;
    s.status = "COMPLETED";
    BigDecimal total = BigDecimal.ZERO;
    for (var entry : merged.entrySet()) {
      Product p =
          products
              .findLocked(entry.getKey())
              .orElseThrow(() -> new IllegalArgumentException("Unknown product"));
      int qty = entry.getValue();
      if (p.archived || p.stock < qty)
        throw new IllegalArgumentException("Insufficient stock: " + p.name);
      p.stock -= qty;
      products.save(p);
      SaleItem li = new SaleItem();
      li.id = UUID.randomUUID();
      li.sale = s;
      li.productId = p.id;
      li.productName = p.name;
      li.quantity = qty;
      li.unitPriceUsd = p.priceUsd;
      li.lineTotalUsd = p.priceUsd.multiply(BigDecimal.valueOf(qty));
      total = total.add(li.lineTotalUsd);
      s.items.add(li);
    }
    s.totalUsd = total.setScale(4, RoundingMode.HALF_UP);
    s.totalKhr = com.retailflow.util.MoneyUtil.toKhr(total, rate);
    return sales.saveAndFlush(s);
  }
}
