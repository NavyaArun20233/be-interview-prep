package com.interviewprep.repository;

import com.interviewprep.entity.Order;
import com.interviewprep.entity.OrderStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);

    /**
     * Inserts a {@code PLACED} order header with a zero total unless the user already has an order with this
     * idempotency key. Returns 1 if inserted, 0 on conflict. If a concurrent transaction holds an uncommitted row with
     * the same key, PostgreSQL waits for it: if it commits this returns 0 and a follow-up read (READ COMMITTED) sees
     * that order; if it rolls back the insert goes ahead and returns 1.
     */
    @Modifying
    @Query(value = """
                    INSERT INTO orders (user_id, status, idempotency_key, request_hash, total_amount,
                                        created_at, updated_at)
                    VALUES (:userId, 'PLACED', :idempotencyKey, :requestHash, 0, :now, :now)
                    ON CONFLICT (user_id, idempotency_key) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("now") Instant now);

    /**
     * Changes the status in one atomic conditional statement. Returns 1 if this call changed it, 0 if the order was not
     * in {@code from} (a concurrent caller waits on the row lock, then re-checks the new status and gets 0).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CustomerOrder o set o.status = :to, o.updatedAt = :now
            where o.id = :id and o.status = :from
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to,
            @Param("now") Instant now);

    default int cancelIfPlaced(long id, Instant now) {
        return updateStatus(id, OrderStatus.PLACED, OrderStatus.CANCELLED, now);
    }
}
