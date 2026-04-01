package com.yunhwan.wit.infrastructure.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.weather.api")
public record WeatherApiProperties(
        String baseUrl,
        String currentPath,
        String forecastPath
) {
}
