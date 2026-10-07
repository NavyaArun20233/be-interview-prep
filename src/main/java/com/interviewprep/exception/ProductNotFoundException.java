package com.interviewprep.exception;

public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(long id) {
        super("Product " + id + " not found");
    }
}
