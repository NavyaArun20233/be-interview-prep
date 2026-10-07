package com.interviewprep.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lines for the same product are merged (quantities added) before stock is reserved. */
public record PlaceOrderRequest(
        @NotNull @Size(min = 1, max = PlaceOrderRequest.MAX_ITEMS)
        List<@NotNull @Valid OrderItemRequest> items) {

    public static final int MAX_ITEMS = 50;
}
