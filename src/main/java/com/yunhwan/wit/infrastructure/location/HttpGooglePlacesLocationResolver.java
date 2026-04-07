package com.yunhwan.wit.infrastructure.location;

import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpGooglePlacesLocationResolver implements GooglePlacesLocationResolver {

    private static final double GOOGLE_PLACES_CONFIDENCE = 0.85;

    private final RestClient googlePlacesRestClient;
    private final GooglePlacesProperties properties;

    public HttpGooglePlacesLocationResolver(
            RestClient googlePlacesRestClient,
            GooglePlacesProperties properties
    ) {
        this.googlePlacesRestClient = Objects.requireNonNull(
                googlePlacesRestClient,
                "googlePlacesRestClient must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public ResolvedLocation resolve(String rawLocation) {
        if (!StringUtils.hasText(rawLocation) || !StringUtils.hasText(properties.apiKey())) {
            return ResolvedLocation.failed(rawLocation);
        }

        try {
            GooglePlacesTextSearchResponse response = googlePlacesRestClient.post()
                    .uri(properties.textSearchPath())
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.fieldMask())
                    .body(new GooglePlacesTextSearchRequest(
                            rawLocation,
                            properties.languageCode(),
                            properties.regionCode(),
                            properties.pageSize()
                    ))
                    .retrieve()
                    .body(GooglePlacesTextSearchResponse.class);

            return mapFirstPlace(rawLocation, response);
        } catch (RestClientResponseException exception) {
            throw new GooglePlacesInfrastructureException("Google Places request failed", exception);
        } catch (RestClientException exception) {
            throw new GooglePlacesInfrastructureException("Google Places communication failed", exception);
        }
    }

    private ResolvedLocation mapFirstPlace(String rawLocation, GooglePlacesTextSearchResponse response) {
        if (response == null || response.places() == null || response.places().isEmpty()) {
            return ResolvedLocation.failed(rawLocation);
        }

        GooglePlacesTextSearchResponse.GooglePlace place = response.places().getFirst();
        if (place.location() == null || place.location().latitude() == null || place.location().longitude() == null) {
            return ResolvedLocation.failed(rawLocation);
        }

        String displayName = displayName(place);
        if (!StringUtils.hasText(displayName)) {
            return ResolvedLocation.failed(rawLocation);
        }

        String displayLocation = StringUtils.hasText(place.formattedAddress())
                ? place.formattedAddress()
                : displayName;

        return ResolvedLocation.resolved(
                rawLocation,
                displayName,
                displayLocation,
                place.location().latitude(),
                place.location().longitude(),
                GOOGLE_PLACES_CONFIDENCE,
                LocationResolvedBy.GOOGLE_PLACES
        );
    }

    private String displayName(GooglePlacesTextSearchResponse.GooglePlace place) {
        if (place.displayName() == null) {
            return null;
        }

        return place.displayName().text();
    }
}
