package com.retailflow.repository;

import com.retailflow.entity.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<StoreSettings, Long> {}
