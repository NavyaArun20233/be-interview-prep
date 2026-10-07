package com.interviewprep.exception;

public class IdempotencyKeyReusedException extends BusinessRuleViolationException {

    public IdempotencyKeyReusedException() {
        super("Idempotency-Key was already used with a different request");
    }
}
