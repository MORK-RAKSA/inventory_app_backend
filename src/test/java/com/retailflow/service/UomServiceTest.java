package com.retailflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.retailflow.dto.UomConfigInput;
import com.retailflow.entity.Product;
import com.retailflow.entity.UomConfig;
import com.retailflow.exception.NotFoundException;
import com.retailflow.repository.ProductRepository;
import com.retailflow.repository.UomConfigRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UomServiceTest {
  private final UomConfigRepository configs = mock(UomConfigRepository.class);
  private final ProductRepository products = mock(ProductRepository.class);
  private final UomService service = new UomService(configs, products);

  @Test
  void updateSynchronizesMatchingProductsAndRecalculatesCasePrice() {
    UomConfig config = new UomConfig("uom-1", "Beverages", "Can", "Case", 24, "");
    Product matching = new Product();
    matching.baseUnit = "Can";
    matching.caseUnitName = "Case";
    matching.wholesalePriceUsd = new BigDecimal("0.50");
    Product unrelated = new Product();
    unrelated.baseUnit = "Bottle";
    unrelated.caseUnitName = "Crate";
    when(configs.findById("uom-1")).thenReturn(Optional.of(config));
    when(configs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(products.findAll()).thenReturn(List.of(matching, unrelated));

    UomConfig updated =
        service.update("uom-1", new UomConfigInput(" Six pack ", "Can", "Pack", 6, ""));

    assertThat(updated.name).isEqualTo("Six pack");
    assertThat(matching.caseQty).isEqualTo(6);
    assertThat(matching.caseUnitName).isEqualTo("Pack");
    assertThat(matching.wholesalePricePerCaseUsd).isEqualByComparingTo("3.00");
    verify(products).save(matching);
    verify(products, never()).save(unrelated);
  }

  @Test
  void missingConfigDoesNotModifyProducts() {
    when(configs.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.update("missing", new UomConfigInput("Pack", "Can", "Pack", 6, "")))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(products);
  }
}
