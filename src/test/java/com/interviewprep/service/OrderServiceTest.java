package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.dto.order.OrderItemRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    @Test
    void equivalentRequestsHaveTheSameHash() {
        String hash = OrderService.requestHash(OrderService.mergeItems(
                List.of(new OrderItemRequest(7L, 1), new OrderItemRequest(3L, 2), new OrderItemRequest(7L, 2))));
        String reordered = OrderService.requestHash(
                OrderService.mergeItems(List.of(new OrderItemRequest(3L, 2), new OrderItemRequest(7L, 3))));
        String different = OrderService.requestHash(
                OrderService.mergeItems(List.of(new OrderItemRequest(3L, 2), new OrderItemRequest(7L, 4))));

        assertThat(OrderService.mergeItems(List.of(new OrderItemRequest(7L, 1), new OrderItemRequest(7L, 2))))
                .containsExactly(Map.entry(7L, 3));
        assertThat(hash).hasSize(64).isEqualTo(reordered).isNotEqualTo(different);
    }
}
