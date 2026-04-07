package com.yunhwan.wit.infrastructure.location;

import java.util.List;

public record GooglePlacesTextSearchResponse(
        List<GooglePlace> places
) {

    public record GooglePlace(
            String id,
            GooglePlaceDisplayName displayName,
            String formattedAddress,
            GooglePlaceLocation location
    ) {
    }

    public record GooglePlaceDisplayName(
            String text
    ) {
    }

    public record GooglePlaceLocation(
            Double latitude,
            Double longitude
    ) {
    }
}
