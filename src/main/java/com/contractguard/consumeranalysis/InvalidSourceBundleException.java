package com.contractguard.consumeranalysis;

/** An uploaded consumer source bundle was unusable. Mapped to HTTP 400. */
public class InvalidSourceBundleException extends RuntimeException {

    public InvalidSourceBundleException(String message) {
        super(message);
    }
}
