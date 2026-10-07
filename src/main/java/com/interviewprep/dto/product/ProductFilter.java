package com.interviewprep.dto.product;

import java.math.BigDecimal;

/** Optional list filters, combined with AND; a {@code null} field means "no restriction". */
public record ProductFilter(
        String category, BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly, String nameContains) {}
