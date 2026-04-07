package com.yunhwan.wit.infrastructure.google;

public class GoogleIntegrationInfrastructureException extends RuntimeException {

    public GoogleIntegrationInfrastructureException(String message) {
        super(message);
    }

    public GoogleIntegrationInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
