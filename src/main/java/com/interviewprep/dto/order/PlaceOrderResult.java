package com.interviewprep.dto.order;

/** {@code created} is false when the request was a retry and {@code order} is the one created by the first attempt. */
public record PlaceOrderResult(OrderResponse order, boolean created) {}
