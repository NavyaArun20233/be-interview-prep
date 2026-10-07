package com.interviewprep.dto.product;

import java.math.BigDecimal;

/** A successful stock reservation, with the product's name and price at the time it was reserved. */
public record ReservedProduct(long productId, String name, BigDecimal unitPrice, int quantity) {}
