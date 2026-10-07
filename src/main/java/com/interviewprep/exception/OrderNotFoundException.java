package com.interviewprep.exception;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(long id) {
        super("Order " + id + " not found");
    }
}
