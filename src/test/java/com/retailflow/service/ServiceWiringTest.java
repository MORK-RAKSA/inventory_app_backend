package com.retailflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.retailflow.config.R2StorageClient;
import com.retailflow.repository.*;
import com.retailflow.service.implement.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ServiceWiringTest {
  @Test
  void springResolvesLombokConstructorsWithOptionalStorageDisabled() {
    new ApplicationContextRunner()
        .withBean(ProductRepository.class, () -> mock(ProductRepository.class))
        .withBean(StockMovementRepository.class, () -> mock(StockMovementRepository.class))
        .withBean(SettingsRepository.class, () -> mock(SettingsRepository.class))
        .withBean(SaleRepository.class, () -> mock(SaleRepository.class))
        .withBean(UomConfigRepository.class, () -> mock(UomConfigRepository.class))
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withUserConfiguration(
            R2StorageClient.class,
            ImageStorageService.class,
            ProductService.class,
            SalesService.class,
            SettingsService.class,
            UomService.class,
            UserServiceImpl.class,
            LiveEventService.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ProductService.class);
              assertThat(context).hasSingleBean(SalesService.class);
              assertThat(context).hasSingleBean(SettingsService.class);
              assertThat(context).hasSingleBean(UomService.class);
              assertThat(context).hasSingleBean(UserServiceImpl.class);
              assertThat(context.getBean(ImageStorageService.class).enabled()).isFalse();
            });
  }
}
