package com.yunhwan.wit.infrastructure.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.location.places.google")
public record GooglePlacesProperties(
        String apiKey,
        String baseUrl,
        String textSearchPath,
        String languageCode,
        String regionCode,
        int pageSize,
        String fieldMask
) {
}
