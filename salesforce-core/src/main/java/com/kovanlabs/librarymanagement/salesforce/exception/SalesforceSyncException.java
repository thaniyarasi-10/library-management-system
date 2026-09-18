package com.kovanlabs.librarymanagement.salesforce.exception;

public class SalesforceSyncException extends RuntimeException {

    public SalesforceSyncException(String message) {
        super(message);
    }

    public SalesforceSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
