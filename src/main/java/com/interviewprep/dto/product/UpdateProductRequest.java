package com.interviewprep.dto.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request body for a full replacement (PUT) of a product; limits mirror the {@code products} table constraints. */
public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String category,

        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @NotNull @Min(0) Integer stock,

        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1)
        BigDecimal rating) {}
