package com.cocos.challenge.domain.exceptions;

public class InconsistentDataException extends RuntimeException {
    public InconsistentDataException(String message) {
        super(message);
    }

    public InconsistentDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
