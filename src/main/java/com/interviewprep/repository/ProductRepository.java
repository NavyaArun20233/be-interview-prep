package com.interviewprep.repository;

import com.interviewprep.entity.Product;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Query("select p.stock from Product p where p.id = :id")
    Optional<Integer> findStockById(@Param("id") long id);

    /**
     * Takes {@code quantity} units in one atomic conditional statement (no read-modify-write): returns 1 if reserved, 0
     * if the product does not exist or has too little stock. Concurrent callers queue on the row lock and re-check the
     * condition against the committed stock, so stock is never oversold. The version is bumped so a concurrent admin
     * update based on an older read fails its optimistic-lock check instead of overwriting the new stock.
     */
    @Modifying
    @Query("""
            update Product p set p.stock = p.stock - :quantity, p.updatedAt = :now, p.version = p.version + 1
            where p.id = :id and p.stock >= :quantity
            """)
    int decrementStockIfAvailable(@Param("id") long id, @Param("quantity") int quantity, @Param("now") Instant now);

    /** Returns {@code quantity} units to stock atomically: 1, or 0 if the product does not exist. */
    @Modifying
    @Query("""
            update Product p set p.stock = p.stock + :quantity, p.updatedAt = :now, p.version = p.version + 1
            where p.id = :id
            """)
    int incrementStock(@Param("id") long id, @Param("quantity") int quantity, @Param("now") Instant now);
}
