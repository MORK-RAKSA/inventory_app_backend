package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.entity.*;
import com.retailflow.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class SalesService {
  private final SaleRepository sales;
  private final ProductRepository products;
  private final SettingsRepository settings;
  private final LiveEventService events;

  public SalesService(
      SaleRepository sales,
      ProductRepository products,
      SettingsRepository settings,
      LiveEventService events) {
    this.sales = sales;
    this.products = products;
    this.settings = settings;
    this.events = events;
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
    Map<UUID, CartLine> firstLineByProduct = new HashMap<>();
    for (CartLine line : in.items()) {
      merged.merge(line.productId(), line.quantity(), Math::addExact);
      firstLineByProduct.putIfAbsent(line.productId(), line);
    }
    // Lock rows deterministically to avoid oversells/deadlocks between cashiers.
    BigDecimal rate = settings.findById(1L).orElseThrow().usdToKhr;
    Sale s = new Sale();
    s.id = UUID.randomUUID();
    s.idempotencyKey = in.idempotencyKey();
    s.paidCurrency = in.paidCurrency();
    s.rateSnapshot = rate;
    s.status = "COMPLETED";
    boolean wholesale = "WHOLESALE".equalsIgnoreCase(in.priceTier());
    s.priceTier = wholesale ? "WHOLESALE" : "RETAIL";
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
      li.unitPriceUsd =
          wholesale && p.wholesalePriceUsd != null && p.wholesalePriceUsd.signum() > 0
              ? p.wholesalePriceUsd
              : p.priceUsd;

      CartLine cl = firstLineByProduct.get(p.id);
      li.cases = cl != null && cl.cases() != null ? cl.cases() : 0;
      li.loose = cl != null && cl.loose() != null ? cl.loose() : 0;
      li.caseUnitName =
          cl != null && cl.caseUnitName() != null ? cl.caseUnitName() : p.caseUnitName;
      li.baseUnit = cl != null && cl.baseUnit() != null ? cl.baseUnit() : p.baseUnit;
      li.uom =
          cl != null && cl.uom() != null
              ? cl.uom()
              : (li.cases > 0
                  ? (li.cases + " " + (li.caseUnitName != null ? li.caseUnitName : "Case"))
                  : null);
      li.isWholesale = wholesale || (cl != null && Boolean.TRUE.equals(cl.isWholesale()));

      if (wholesale
          && li.cases > 0
          && p.wholesalePricePerCaseUsd != null
          && p.wholesalePricePerCaseUsd.signum() > 0) {
        BigDecimal caseTotal = p.wholesalePricePerCaseUsd.multiply(BigDecimal.valueOf(li.cases));
        BigDecimal looseTotal = li.unitPriceUsd.multiply(BigDecimal.valueOf(Math.max(0, li.loose)));
        li.lineTotalUsd = caseTotal.add(looseTotal);
      } else {
        li.lineTotalUsd = li.unitPriceUsd.multiply(BigDecimal.valueOf(qty));
      }

      total = total.add(li.lineTotalUsd);
      s.items.add(li);
    }
    s.totalUsd = total.setScale(4, RoundingMode.HALF_UP);
    s.totalKhr = com.retailflow.util.MoneyUtil.toKhr(total, rate);
    Sale saved = sales.saveAndFlush(s);
    String shortId = saved.id.toString().substring(0, 8).toUpperCase();
    events.push(
        "CHECKOUT",
        "Checkout #" + shortId,
        "ទំនិញបានទូទាត់ #" + shortId,
        String.format("%,d ៛ · %d items", saved.totalKhr.longValue(), saved.items.size()),
        null,
        saved);
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    sales.findById(id).ifPresent(sales::delete);
  }
}
