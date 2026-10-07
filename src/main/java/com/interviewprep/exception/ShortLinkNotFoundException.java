package com.interviewprep.exception;

public class ShortLinkNotFoundException extends ResourceNotFoundException {

    public ShortLinkNotFoundException(String code) {
        super("Short link " + code + " not found");
    }
}
