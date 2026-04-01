package com.yunhwan.wit.infrastructure.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.location.current")
public record CurrentLocationProperties(
        String normalizedQuery,
        String displayLocation,
        double lat,
        double lng
) {
}
