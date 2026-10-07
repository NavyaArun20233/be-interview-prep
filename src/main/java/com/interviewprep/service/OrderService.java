package com.interviewprep.service;

import com.interviewprep.dto.order.OrderItemRequest;
import com.interviewprep.dto.order.OrderItemResponse;
import com.interviewprep.dto.order.OrderResponse;
import com.interviewprep.dto.order.PlaceOrderRequest;
import com.interviewprep.dto.order.PlaceOrderResult;
import com.interviewprep.dto.product.ReservedProduct;
import com.interviewprep.entity.Order;
import com.interviewprep.entity.OrderItem;
import com.interviewprep.exception.IdempotencyKeyReusedException;
import com.interviewprep.exception.OrderNotFoundException;
import com.interviewprep.repository.OrderItemRepository;
import com.interviewprep.repository.OrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders are all-or-nothing: the order header, the stock reservation of every item and the items are written in one
 * transaction, which any failure rolls back entirely. Stock is reserved by atomic conditional updates in the product
 * service, so concurrent orders can never oversell.
 *
 * <p>Retries are recognised by the client's idempotency key, scoped to the user. The header is inserted first with
 * {@code ON CONFLICT DO NOTHING}; a concurrent request with the same key waits for the first transaction and then
 * replays its order. A failed attempt (e.g. insufficient stock) rolls back, so its key stays free and a retry is
 * evaluated again from scratch.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductService productService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productService = productService;
        this.clock = clock;
    }

    /**
     * Places an order, or returns the order already placed by this user with this key ({@code created = false}).
     *
     * @throws IdempotencyKeyReusedException if the key was used for a different request
     */
    @Transactional
    public PlaceOrderResult place(long userId, String idempotencyKey, PlaceOrderRequest request) {
        SortedMap<Long, Integer> quantities = mergeItems(request.items());
        String requestHash = requestHash(quantities);
        Instant now = now();

        if (orderRepository.insertIfAbsent(userId, idempotencyKey, requestHash, now) == 0) {
            Order existing = orderRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Order for an existing idempotency key not found"));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            log.info("Replayed order {} for a retried request", existing.getId());
            return new PlaceOrderResult(toResponse(existing), false);
        }

        Order order = orderRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Inserted order not found"));
        List<ReservedProduct> reserved = productService.reserveStock(quantities);
        List<OrderItem> items = orderItemRepository.saveAll(reserved.stream()
                .map(r -> new OrderItem(order.getId(), r.productId(), r.quantity(), r.unitPrice()))
                .toList());
        BigDecimal total = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.recordTotal(total, now);
        log.info("Placed order {} with {} item(s)", order.getId(), items.size());

        Map<Long, String> names =
                reserved.stream().collect(Collectors.toMap(ReservedProduct::productId, ReservedProduct::name));
        return new PlaceOrderResult(toResponse(order, items, names), true);
    }

    /** Owners and admins only; anyone else gets 404 so the order's existence is not revealed. */
    @Transactional(readOnly = true)
    public OrderResponse get(long id, long userId, boolean admin) {
        return toResponse(findAccessible(id, userId, admin));
    }

    /**
     * Cancels a placed order and returns its stock. Idempotent: cancelling a cancelled order returns it unchanged and
     * never restocks twice, because only the call that flips the status restocks.
     */
    @Transactional
    public OrderResponse cancel(long id, long userId, boolean admin) {
        findAccessible(id, userId, admin);
        if (orderRepository.cancelIfPlaced(id, now()) == 1) {
            SortedMap<Long, Integer> quantities = new TreeMap<>();
            orderItemRepository
                    .findByOrderIdOrderByProductId(id)
                    .forEach(item -> quantities.put(item.getProductId(), item.getQuantity()));
            productService.restock(quantities);
            log.info("Cancelled order {}", id);
        }
        // The status update cleared the persistence context, so this reads the current row.
        return toResponse(orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id)));
    }

    /** Merges lines for the same product and orders them by product id: the canonical form of a request. */
    static SortedMap<Long, Integer> mergeItems(List<OrderItemRequest> items) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        return quantities;
    }

    /** SHA-256 of the canonical request, so equivalent requests (line order, split lines) hash the same. */
    static String requestHash(SortedMap<Long, Integer> quantities) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private Order findAccessible(long id, long userId, boolean admin) {
        return orderRepository
                .findById(id)
                .filter(order -> admin || order.isOwnedBy(userId))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByProductId(order.getId());
        Map<Long, String> names =
                productService.names(items.stream().map(OrderItem::getProductId).toList());
        return toResponse(order, items, names);
    }

    private static OrderResponse toResponse(Order order, List<OrderItem> items, Map<Long, String> names) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(), names.get(item.getProductId()), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getStatus(), itemResponses, order.getTotalAmount(), order.getCreatedAt());
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
