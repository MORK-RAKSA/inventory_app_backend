package com.retailflow.sales;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
  Optional<Sale> findByIdempotencyKey(String key);

  @Query(
      "select distinct s from Sale s where s.createdAt>=:from and s.createdAt<:to order by s.createdAt desc")
  List<Sale> byDate(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
