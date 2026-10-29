package com.djorka.incidentops.service;

public class EmailDeliveryException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public EmailDeliveryException(String errorCode, boolean retryable, Throwable cause) {
        super("Transactional email delivery failed", cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public EmailDeliveryException(String errorCode, boolean retryable) {
        this(errorCode, retryable, null);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
