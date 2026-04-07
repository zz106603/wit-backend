package com.yunhwan.wit.infrastructure.location;

public record GooglePlacesTextSearchRequest(
        String textQuery,
        String languageCode,
        String regionCode,
        int pageSize
) {
}
