package com.yunhwan.wit.domain.model;

import java.util.Objects;

public record ResolvedLocation(
        String rawLocation,
        String normalizedQuery,
        String displayLocation,
        Double lat,
        Double lng,
        Double confidence,
        LocationResolutionStatus status,
        LocationResolvedBy resolvedBy
) {

    public ResolvedLocation {
        Objects.requireNonNull(status, "status must not be null");

        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }

        if (status == LocationResolutionStatus.FAILED) {
            if (normalizedQuery != null || displayLocation != null || lat != null || lng != null
                    || confidence != null || resolvedBy != null) {
                throw new IllegalArgumentException("failed location must not contain resolved values");
            }
        } else {
            Objects.requireNonNull(normalizedQuery, "normalizedQuery must not be null");
            Objects.requireNonNull(displayLocation, "displayLocation must not be null");
            Objects.requireNonNull(lat, "lat must not be null");
            Objects.requireNonNull(lng, "lng must not be null");
            Objects.requireNonNull(confidence, "confidence must not be null");
            Objects.requireNonNull(resolvedBy, "resolvedBy must not be null");
        }
    }

    public static ResolvedLocation resolved(
            String rawLocation,
            String normalizedQuery,
            String displayLocation,
            double lat,
            double lng,
            double confidence,
            LocationResolvedBy resolvedBy
    ) {
        return new ResolvedLocation(
                rawLocation,
                normalizedQuery,
                displayLocation,
                lat,
                lng,
                confidence,
                LocationResolutionStatus.RESOLVED,
                resolvedBy
        );
    }

    public static ResolvedLocation approximated(
            String rawLocation,
            String normalizedQuery,
            String displayLocation,
            double lat,
            double lng,
            double confidence,
            LocationResolvedBy resolvedBy
    ) {
        return new ResolvedLocation(
                rawLocation,
                normalizedQuery,
                displayLocation,
                lat,
                lng,
                confidence,
                LocationResolutionStatus.APPROXIMATED,
                resolvedBy
        );
    }

    public static ResolvedLocation failed(String rawLocation) {
        return new ResolvedLocation(rawLocation, null, null, null, null, null, LocationResolutionStatus.FAILED, null);
    }
}
