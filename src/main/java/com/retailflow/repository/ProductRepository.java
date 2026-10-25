package com.retailflow.repository;

import com.retailflow.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
  List<Product> findAllByArchivedFalseOrderByNameAsc();

  Optional<Product> findByBarcodeAndArchivedFalse(String barcode);

  /** Any product (active or archived) holding this barcode. */
  Optional<Product> findFirstByBarcode(String barcode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id=:id")
  Optional<Product> findLocked(@Param("id") UUID id);
}
