package com.interviewprep.exception;

public class ProductInUseException extends ResourceConflictException {

    public ProductInUseException(long id) {
        super("Product " + id + " is part of existing orders and cannot be deleted");
    }
}
