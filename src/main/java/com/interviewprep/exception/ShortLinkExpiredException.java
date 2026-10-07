package com.interviewprep.exception;

public class ShortLinkExpiredException extends ResourceGoneException {

    public ShortLinkExpiredException(String code) {
        super("Short link " + code + " has expired");
    }
}
