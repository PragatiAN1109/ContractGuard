package com.contractguard.schema;

/** The submitted content is not a valid Avro schema. Mapped to HTTP 400. */
public class InvalidAvroSchemaException extends RuntimeException {

    public InvalidAvroSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
