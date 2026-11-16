package com.parko.identity.service.exception;

public class RegistrationLimitExceededException extends RuntimeException {

    public RegistrationLimitExceededException(String message) {
        super(message);
    }
}
