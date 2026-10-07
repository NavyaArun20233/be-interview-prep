package com.interviewprep.dto.product;

import com.interviewprep.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable, so one instance can safely be shared from the {@code products} cache. */
public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        BigDecimal rating,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.getRating(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
