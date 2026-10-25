package com.retailflow.repository;

import com.retailflow.entity.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {}
