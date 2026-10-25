package com.retailflow.dto;

import com.retailflow.entity.Product;
import java.util.List;

public record BatchResult(int created, int restocked, List<Product> products) {}
