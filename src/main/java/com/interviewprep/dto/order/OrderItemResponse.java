package com.interviewprep.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(long productId, String productName, int quantity, BigDecimal unitPrice) {}
