package com.interviewprep.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process Caffeine cache for single-product lookups. Transaction-aware: puts and evictions issued inside a
 * transaction are applied only after it commits, so a rolled-back update never reaches the cache. Bounded in size and
 * age as a safety net; hit/miss stats are recorded and exposed by Actuator as {@code cache.*} metrics.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        // Fixed names: the cache exists at startup (so its metrics are bound) and no other cache can be created.
        caffeine.setCacheNames(List.of(PRODUCTS_CACHE));
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
