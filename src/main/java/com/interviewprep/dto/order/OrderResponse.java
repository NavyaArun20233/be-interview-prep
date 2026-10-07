package com.interviewprep.dto.order;

import com.interviewprep.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, OrderStatus status, List<OrderItemResponse> items, BigDecimal totalAmount, Instant createdAt) {}
