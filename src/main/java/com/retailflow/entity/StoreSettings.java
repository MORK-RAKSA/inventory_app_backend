package com.retailflow.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "store_settings")
public class StoreSettings {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Id public Long id;

  @Column(name = "usd_to_khr")
  public BigDecimal usdToKhr;

  @Column(name = "categories_json", columnDefinition = "text")
  public String categoriesJson;

  @Column(name = "base_units_json", columnDefinition = "text")
  public String baseUnitsJson;

  @Column(name = "updated_at")
  public java.time.OffsetDateTime updatedAt;

  protected StoreSettings() {}

  @JsonProperty("categories")
  public List<String> getCategories() {
    if (categoriesJson == null || categoriesJson.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return MAPPER.readValue(categoriesJson, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public void setCategories(List<String> list) {
    if (list == null) {
      this.categoriesJson = "[]";
    } else {
      try {
        this.categoriesJson = MAPPER.writeValueAsString(list);
      } catch (Exception e) {
        this.categoriesJson = "[]";
      }
    }
  }

  @JsonProperty("baseUnits")
  public List<String> getBaseUnits() {
    if (baseUnitsJson == null || baseUnitsJson.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return MAPPER.readValue(baseUnitsJson, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public void setBaseUnits(List<String> list) {
    if (list == null) {
      this.baseUnitsJson = "[]";
    } else {
      try {
        this.baseUnitsJson = MAPPER.writeValueAsString(list);
      } catch (Exception e) {
        this.baseUnitsJson = "[]";
      }
    }
  }
}
