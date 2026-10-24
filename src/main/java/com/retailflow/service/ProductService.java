package com.retailflow.service;

import com.retailflow.dto.*;
import com.retailflow.globalExceptional.ConflictException;
import com.retailflow.globalExceptional.NotFoundException;
import com.retailflow.product.*;
import com.retailflow.settings.SettingsRepository;
import com.retailflow.util.MoneyUtil;
import java.math.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@org.springframework.stereotype.Service
public class ProductService {
  private final ProductRepository repo;
  private final StockMovementRepository movements;
  private final SettingsRepository settings;
  private final ImageStorageService images;

  public ProductService(
      ProductRepository repo,
      StockMovementRepository movements,
      SettingsRepository settings,
      ImageStorageService images) {
    this.repo = repo;
    this.movements = movements;
    this.settings = settings;
    this.images = images;
  }

  public List<Product> list() {
    return repo.findAllByArchivedFalseOrderByNameAsc();
  }

  public Product get(UUID id) {
    return repo.findById(id)
        .filter(p -> !p.archived)
        .orElseThrow(() -> new NotFoundException("Product not found"));
  }

  public Product barcode(String value) {
    return repo.findByBarcodeAndArchivedFalse(value)
        .orElseThrow(() -> new NotFoundException("Unknown barcode"));
  }

  private static String cleanBarcode(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private BigDecimal rate() {
    return settings.findById(1L).orElseThrow().usdToKhr;
  }

  /** Copies the editable descriptive/price fields. Barcode and stock are handled by the callers. */
  private void applyDetails(
      Product p, String name, String category, BigDecimal price, String ccy, int threshold) {
    MoneyUtil.requireCurrency(ccy);
    BigDecimal rate = rate();
    p.name = name.trim();
    p.category = category.trim();
    p.originalCcy = ccy;
    p.originalPrice = price;
    p.priceFxRate = rate;
    p.priceUsd = MoneyUtil.toUsd(price, ccy, rate);
    p.lowStockThreshold = threshold;
  }

  private Product newProduct(
      String name,
      String barcode,
      String category,
      BigDecimal price,
      String ccy,
      int stock,
      int threshold,
      String reason) {
    String code = cleanBarcode(barcode);
    if (code != null && repo.findByBarcodeAndArchivedFalse(code).isPresent())
      throw new ConflictException("Barcode already exists: " + code);
    // An archived product may still hold this barcode (the H2 schema keeps a global UNIQUE
    // constraint). Revive it instead of failing the whole batch with a DB conflict.
    if (code != null) {
      Optional<Product> archived = repo.findFirstByBarcode(code);
      if (archived.isPresent()) {
        Product p = archived.get();
        p.archived = false;
        applyDetails(p, name, category, price, ccy, threshold);
        int delta = stock - p.stock;
        p.stock = stock;
        repo.save(p);
        if (delta != 0) movements.save(new StockMovement(p.id, delta, reason, null));
        return p;
      }
    }
    Product p = new Product();
    p.id = UUID.randomUUID();
    applyDetails(p, name, category, price, ccy, threshold);
    p.barcode = code;
    p.stock = stock;
    repo.save(p);
    if (stock > 0) movements.save(new StockMovement(p.id, stock, reason, null));
    return p;
  }

  @Transactional
  public Product create(ProductInput in) {
    String code = cleanBarcode(in.barcode());
    if (code != null) {
      Optional<Product> existing = repo.findByBarcodeAndArchivedFalse(code);
      if (existing.isPresent()) {
        Product p = existing.get();
        applyDetails(p, in.name(), in.category(), in.price(), in.currency(), in.lowStockThreshold());
        if (in.expirationDate() != null) p.expirationDate = in.expirationDate();
        if (in.stock() > 0) {
          p.stock = Math.addExact(p.stock, in.stock());
          movements.save(new StockMovement(p.id, in.stock(), "RESTOCK", null));
        }
        return repo.save(p);
      }
    }
    Product product =
        newProduct(
            in.name(),
            in.barcode(),
            in.category(),
            in.price(),
            in.currency(),
            in.stock(),
            in.lowStockThreshold(),
            "INITIAL");
    product.expirationDate = in.expirationDate();
    return repo.save(product);
  }

  /**
   * Updates a product. Stock IS applied (the edit form owns the stock count) and the difference is
   * logged as a stock movement. The barcode of an existing product is immutable; it can only be set
   * when the product has none yet.
   */
  @Transactional
  public Product edit(UUID id, ProductInput in) {
    Product p =
        repo.findLocked(id)
            .filter(x -> !x.archived)
            .orElseThrow(() -> new NotFoundException("Product not found"));
    applyDetails(p, in.name(), in.category(), in.price(), in.currency(), in.lowStockThreshold());
    p.expirationDate = in.expirationDate();
    String code = cleanBarcode(in.barcode());
    if (p.barcode == null && code != null) {
      if (repo.findByBarcodeAndArchivedFalse(code).isPresent())
        throw new ConflictException("Barcode already exists: " + code);
      p.barcode = code;
    }
    int delta = in.stock() - p.stock;
    if (delta != 0) {
      p.stock = in.stock();
      movements.save(new StockMovement(p.id, delta, "MANUAL_EDIT", null));
    }
    return repo.save(p);
  }

  @Transactional
  public Product restock(UUID id, StockInput in) {
    Product p = repo.findLocked(id).orElseThrow(() -> new NotFoundException("Unknown product"));
    if (p.archived) throw new IllegalArgumentException("Archived product");
    p.stock = Math.addExact(p.stock, in.quantity());
    movements.save(new StockMovement(p.id, in.quantity(), "RESTOCK", null));
    return repo.save(p);
  }

  @Transactional
  public void archive(UUID id) {
    Product p = get(id);
    p.archived = true;
    repo.save(p);
  }

  /**
   * Atomically applies a whole batch: restocks existing products and creates new ones. Any invalid
   * line rolls the entire batch back, so there are never partial batches.
   */
  @Transactional
  public BatchResult commitBatch(BatchCommitRequest req) {
    Map<UUID, Integer> restocks = new TreeMap<>(); // sorted => consistent lock order, no deadlocks
    Map<String, BatchItem> byBarcode = new LinkedHashMap<>();
    List<BatchItem> noBarcode = new ArrayList<>();
    Map<String, Integer> byBarcodeQty = new HashMap<>();

    int line = 0;
    for (BatchItem it : req.items()) {
      line++;
      String where = "Item " + line + ": ";
      if (it == null) throw new IllegalArgumentException(where + "is empty");
      if (it.productId() != null) {
        int qty = it.quantityAdded() == null ? 0 : it.quantityAdded();
        if (qty < 0) throw new IllegalArgumentException(where + "quantityAdded cannot be negative");
        if (qty > 0) restocks.merge(it.productId(), qty, Math::addExact);
        else restocks.putIfAbsent(it.productId(), 0);
        continue;
      }
      if (it.name() == null || it.name().isBlank())
        throw new IllegalArgumentException(where + "name is required");
      if (it.category() == null || it.category().isBlank())
        throw new IllegalArgumentException(where + "category is required");
      if (it.price() == null || it.price().signum() < 0)
        throw new IllegalArgumentException(where + "price must be 0 or more");
      if (it.stock() == null || it.stock() < 0)
        throw new IllegalArgumentException(where + "stock must be 0 or more");
      if (it.lowStockThreshold() != null && it.lowStockThreshold() < 0)
        throw new IllegalArgumentException(where + "lowStockThreshold must be 0 or more");
      MoneyUtil.requireCurrency(it.currency() == null ? "USD" : it.currency());

      String code = cleanBarcode(it.barcode());
      if (code == null) {
        noBarcode.add(it);
        continue;
      }
      Optional<Product> existing = repo.findByBarcodeAndArchivedFalse(code);
      if (existing.isPresent()) { // same barcode already in the catalogue => treat as a restock
        restocks.merge(existing.get().id, it.stock(), Math::addExact);
      } else if (byBarcode.containsKey(code)) { // same new barcode scanned twice => add quantities
        byBarcodeQty.merge(code, it.stock(), Math::addExact);
      } else {
        byBarcode.put(code, it);
        byBarcodeQty.put(code, it.stock());
      }
    }

    List<Product> touched = new ArrayList<>();
    int restocked = 0;
    for (var e : restocks.entrySet()) {
      Product p =
          repo.findLocked(e.getKey())
              .filter(x -> !x.archived)
              .orElseThrow(() -> new NotFoundException("Product not found: " + e.getKey()));
      if (e.getValue() > 0) {
        p.stock = Math.addExact(p.stock, e.getValue());
        movements.save(new StockMovement(p.id, e.getValue(), "BATCH_RESTOCK", null));
        repo.save(p);
        restocked++;
      }
      touched.add(p);
    }

    int created = 0;
    for (var e : byBarcode.entrySet()) {
      BatchItem it = e.getValue();
      touched.add(
          newProduct(
              it.name(),
              e.getKey(),
              it.category(),
              it.price(),
              ccy(it),
              byBarcodeQty.get(e.getKey()),
              threshold(it),
              "BATCH_CREATE"));
      created++;
    }
    for (BatchItem it : noBarcode) {
      touched.add(
          newProduct(
              it.name(),
              null,
              it.category(),
              it.price(),
              ccy(it),
              it.stock(),
              threshold(it),
              "BATCH_CREATE"));
      created++;
    }
    return new BatchResult(created, restocked, touched);
  }

  private static String ccy(BatchItem it) {
    return it.currency() == null ? "USD" : it.currency();
  }

  private static int threshold(BatchItem it) {
    return it.lowStockThreshold() == null ? 5 : it.lowStockThreshold();
  }

  /** Uploads to Cloudflare R2 (when configured) and stores the public URL on the product. */
  @Transactional
  public Product setImage(UUID id, MultipartFile file) {
    Product p = get(id);
    ImageStorageService.Stored stored = images.upload(id, file);
    String oldKey = p.imageKey;
    p.imageUrl = stored.url();
    p.imageKey = stored.key();
    Product saved = repo.save(p);
    images.delete(oldKey);
    return saved;
  }

  @Transactional
  public Product removeImage(UUID id) {
    Product p = get(id);
    String oldKey = p.imageKey;
    p.imageUrl = null;
    p.imageKey = null;
    Product saved = repo.save(p);
    images.delete(oldKey);
    return saved;
  }
}
