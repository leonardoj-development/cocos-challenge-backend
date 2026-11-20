package com.cocos.challenge.domain.exceptions;

public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String message) {
        super(message);
    }

    public OrderNotCancellableException(String message, Throwable cause) {
        super(message, cause);
    }
}
