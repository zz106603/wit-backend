package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleIntegrationUnavailableException;

public class GoogleIntegrationInfrastructureException extends GoogleIntegrationUnavailableException {

    public GoogleIntegrationInfrastructureException(String message) {
        super(message);
    }

    public GoogleIntegrationInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
