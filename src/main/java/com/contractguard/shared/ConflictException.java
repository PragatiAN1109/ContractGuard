package com.contractguard.shared;

/** The request conflicts with existing state. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
