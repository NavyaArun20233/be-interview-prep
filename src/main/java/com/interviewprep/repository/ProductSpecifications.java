package com.interviewprep.repository;

import com.interviewprep.dto.product.ProductFilter;
import com.interviewprep.entity.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Builds the product list query from optional filters (AND-combined); every value is a bound parameter. */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {}

    public static Specification<Product> matching(ProductFilter filter) {
        List<Specification<Product>> specs = new ArrayList<>();
        if (filter.category() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category"), filter.category()));
        }
        if (filter.minPrice() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
        }
        if (filter.inStockOnly()) {
            specs.add((root, query, cb) -> cb.greaterThan(root.get("stock"), 0));
        }
        if (filter.nameContains() != null) {
            String pattern = "%" + escapeLike(filter.nameContains().toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
        }
        return Specification.allOf(specs);
    }

    /** Treats {@code %} and {@code _} in the search text literally. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
