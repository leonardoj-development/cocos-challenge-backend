package com.cocos.challenge.domain.exceptions;

public class InstrumentNotFoundException extends RuntimeException {
    public InstrumentNotFoundException(String message) {
        super(message);
    }

    public InstrumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
