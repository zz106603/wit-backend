package com.yunhwan.wit.infrastructure.weather;

public class WeatherInfrastructureException extends RuntimeException {

    public WeatherInfrastructureException(String message) {
        super(message);
    }

    public WeatherInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
