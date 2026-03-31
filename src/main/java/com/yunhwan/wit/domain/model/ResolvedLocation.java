package com.yunhwan.wit.domain.model;

import java.util.Objects;

public record ResolvedLocation(
        String rawLocation,
        String normalizedQuery,
        String displayLocation,
        double lat,
        double lng,
        double confidence,
        LocationResolutionStatus status,
        LocationResolvedBy resolvedBy
) {

    public ResolvedLocation {
        Objects.requireNonNull(status, "status must not be null");
    }
}
