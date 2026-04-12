package com.yunhwan.wit.infrastructure.ai;

public class GeminiInfrastructureException extends RuntimeException {

    public GeminiInfrastructureException(String message) {
        super(message);
    }

    public GeminiInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
