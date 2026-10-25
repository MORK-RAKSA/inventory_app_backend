package com.retailflow.repository;

import com.retailflow.entity.UomConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UomConfigRepository extends JpaRepository<UomConfig, String> {
  List<UomConfig> findAllByOrderByNameAsc();
}
