package com.fiap.workshop.management.domain.exception;

public class SupplyCatalogUnavailableException extends RuntimeException {

    public SupplyCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SupplyCatalogUnavailableException(String message) {
        super(message);
    }
}
