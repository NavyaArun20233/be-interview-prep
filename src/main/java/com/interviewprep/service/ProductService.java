package com.interviewprep.service;

import com.interviewprep.config.CacheConfig;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.product.ProductFilter;
import com.interviewprep.dto.product.ProductResponse;
import com.interviewprep.dto.product.UpdateProductRequest;
import com.interviewprep.entity.Product;
import com.interviewprep.exception.FieldValidationException;
import com.interviewprep.exception.ProductNotFoundException;
import com.interviewprep.repository.ProductRepository;
import com.interviewprep.repository.ProductSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "category", "price", "stock", "rating", "createdAt");
    private static final String SORT_PARAM = "sort";

    private final ProductRepository productRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    /**
     * Pages are capped at {@link #MAX_PAGE_SIZE} (larger requests are clamped, not rejected). {@code sort} is
     * {@code field[,asc|desc]}; {@code id} is always appended as a tie-breaker so pages are stable.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductFilter filter, int page, int size, String sort) {
        if (filter.minPrice() != null
                && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new FieldValidationException("minPrice", "must be less than or equal to maxPrice");
        }
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), parseSort(sort));
        return PageResponse.from(
                productRepository.findAll(ProductSpecifications.matching(filter), pageRequest), ProductResponse::from);
    }

    /** Cached by id; the body (and its log line) only runs on a cache miss. */
    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        log.debug("Loading product {} from database", id);
        return ProductResponse.from(findProduct(id));
    }

    /** Replaces the cached entry with the new state once the transaction commits. */
    @CachePut(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#id")
    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product product = findProduct(id);
        product.update(request.name(), request.category(), request.price(), request.stock(), request.rating(), now());
        // Flush so the optimistic-lock version is checked before the response (and cache entry) is built.
        Product saved = productRepository.saveAndFlush(product);
        log.info("Updated product {}", id);
        return ProductResponse.from(saved);
    }

    /** Evicts the cached entry once the transaction commits. */
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#id")
    @Transactional
    public void delete(long id) {
        productRepository.delete(findProduct(id));
        log.info("Deleted product {}", id);
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("id"));
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field) || parts.length > 2) {
            throw new FieldValidationException(
                    SORT_PARAM, "must be field[,asc|desc] with field one of: " + String.join(", ", sortedFields()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new FieldValidationException(SORT_PARAM, "direction must be asc or desc"));
        }
        Sort result = Sort.by(direction, field);
        return "id".equals(field) ? result : result.and(Sort.by(Sort.Order.asc("id")));
    }

    private static Iterable<String> sortedFields() {
        return SORTABLE_FIELDS.stream().sorted().toList();
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Product findProduct(long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
