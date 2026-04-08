package com.yunhwan.wit.infrastructure.location;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.location.current")
public record CurrentLocationProperties(
        String normalizedQuery,
        String displayLocation,
        double lat,
        double lng
) {

    private static final String DEFAULT_NORMALIZED_QUERY = "current";
    private static final String DEFAULT_DISPLAY_LOCATION = "서울특별시 강남구";
    private static final double DEFAULT_LAT = 37.5172;
    private static final double DEFAULT_LNG = 127.0473;

    public boolean isDefaultLocation() {
        return DEFAULT_NORMALIZED_QUERY.equals(normalizedQuery)
                && DEFAULT_DISPLAY_LOCATION.equals(displayLocation)
                && Double.compare(DEFAULT_LAT, lat) == 0
                && Double.compare(DEFAULT_LNG, lng) == 0;
    }
}
