package com.interviewprep.controller;

import com.interviewprep.dto.order.OrderResponse;
import com.interviewprep.dto.order.PlaceOrderRequest;
import com.interviewprep.dto.order.PlaceOrderResult;
import com.interviewprep.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Any authenticated user can order; an order is visible to and cancellable by its owner and ADMINs only. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 201 for a new order; 200 with {@code Idempotent-Replayed: true} when the key matches an earlier request. */
    @PostMapping
    public ResponseEntity<OrderResponse> place(
            Authentication authentication,
            @RequestHeader(IDEMPOTENCY_KEY)
                    @Pattern(
                            regexp = "[A-Za-z0-9_-]{1,100}",
                            message = "must be 1-100 characters of A-Z, a-z, 0-9, _ or -")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = orderService.place(userId(authentication), idempotencyKey, request);
        if (!result.created()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true").body(result.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.order().id())
                .toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication authentication, @PathVariable long id) {
        return orderService.get(id, userId(authentication), isAdmin(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication authentication, @PathVariable long id) {
        return orderService.cancel(id, userId(authentication), isAdmin(authentication));
    }

    /** The access token's subject is the user id. */
    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
