package com.yunhwan.wit.infrastructure.weather;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.weather.cache")
public record WeatherCacheProperties(
        Duration ttl
) {
}
