package com.interviewprep.exception;

public class InsufficientStockException extends ResourceConflictException {

    public InsufficientStockException(long productId, int requested, int available) {
        super("Insufficient stock for product " + productId + ": requested " + requested + ", available " + available);
    }
}
