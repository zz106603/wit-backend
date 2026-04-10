package com.yunhwan.wit.infrastructure.location;

import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpGooglePlacesLocationResolver implements GooglePlacesLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(HttpGooglePlacesLocationResolver.class);
    private static final double GOOGLE_PLACES_RESOLVED_CONFIDENCE = 0.85;
    private static final double GOOGLE_PLACES_APPROXIMATED_CONFIDENCE = 0.65;
    private static final Set<String> GENERIC_CONTEXT_TOKENS = Set.of(
            "회사", "회식", "미팅", "약속", "점심", "저녁", "아침", "모임", "식사", "방문", "일정",
            "행사", "출근", "퇴근", "맛집", "식당", "카페", "술집", "음식점", "근처", "인근",
            "오전", "오후"
    );

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
            log.info(
                    "[RecommendationDebug] Google Places short-circuit. rawLocation={}, apiKeyPresent={}",
                    rawLocation,
                    StringUtils.hasText(properties.apiKey())
            );
            return ResolvedLocation.failed(rawLocation);
        }

        String placesQuery = normalizePlacesQuery(rawLocation);
        if (!StringUtils.hasText(placesQuery)) {
            log.info(
                    "[RecommendationDebug] Google Places short-circuit after normalization. rawLocation={}, normalizedQuery={}",
                    rawLocation,
                    placesQuery
            );
            return ResolvedLocation.failed(rawLocation);
        }

        try {
            log.info(
                    "[RecommendationDebug] Google Places request before. rawLocation={}, normalizedQuery={}, path={}, fieldMask={}",
                    rawLocation,
                    placesQuery,
                    properties.textSearchPath(),
                    properties.fieldMask()
            );
            GooglePlacesTextSearchResponse response = googlePlacesRestClient.post()
                    .uri(properties.textSearchPath())
                    .header("X-Goog-Api-Key", properties.apiKey())
                    .header("X-Goog-FieldMask", properties.fieldMask())
                    .body(new GooglePlacesTextSearchRequest(
                            placesQuery,
                            properties.languageCode(),
                            properties.regionCode(),
                            properties.pageSize()
                    ))
                    .retrieve()
                    .body(GooglePlacesTextSearchResponse.class);
            log.info(
                    "[RecommendationDebug] Google Places request after. rawLocation={}, itemCount={}",
                    rawLocation,
                    itemCount(response)
            );

            return mapBestPlace(rawLocation, response);
        } catch (RestClientResponseException exception) {
            throw new GooglePlacesInfrastructureException("Google Places request failed", exception);
        } catch (RestClientException exception) {
            throw new GooglePlacesInfrastructureException("Google Places communication failed", exception);
        }
    }

    private int itemCount(GooglePlacesTextSearchResponse response) {
        if (response == null || response.places() == null) {
            return 0;
        }
        return response.places().size();
    }

    private ResolvedLocation mapBestPlace(String rawLocation, GooglePlacesTextSearchResponse response) {
        if (response == null || response.places() == null || response.places().isEmpty()) {
            return ResolvedLocation.failed(rawLocation);
        }

        ResolvedLocation bestApproximated = null;
        for (GooglePlacesTextSearchResponse.GooglePlace place : response.places()) {
            ResolvedLocation candidate = mapPlace(rawLocation, place);
            if (candidate.status() == LocationResolutionStatus.RESOLVED) {
                return candidate;
            }
            if (candidate.status() == LocationResolutionStatus.APPROXIMATED && bestApproximated == null) {
                bestApproximated = candidate;
            }
        }

        if (bestApproximated != null) {
            return bestApproximated;
        }

        return ResolvedLocation.failed(rawLocation);
    }

    private ResolvedLocation mapPlace(String rawLocation, GooglePlacesTextSearchResponse.GooglePlace place) {
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
        LocationResolutionStatus status = evaluateStatus(rawLocation, place, displayName);
        double confidence = status == LocationResolutionStatus.RESOLVED
                ? GOOGLE_PLACES_RESOLVED_CONFIDENCE
                : GOOGLE_PLACES_APPROXIMATED_CONFIDENCE;
        log.info(
                "[RecommendationDebug] Google Places response mapped. rawLocation={}, displayName={}, displayLocation={}, lat={}, lng={}, status={}, confidence={}",
                rawLocation,
                displayName,
                displayLocation,
                place.location().latitude(),
                place.location().longitude(),
                status,
                confidence
        );

        if (status == LocationResolutionStatus.RESOLVED) {
            return ResolvedLocation.resolved(
                    rawLocation,
                    displayName,
                    displayLocation,
                    place.location().latitude(),
                    place.location().longitude(),
                    confidence,
                    LocationResolvedBy.GOOGLE_PLACES
            );
        }

        if (status == LocationResolutionStatus.FAILED) {
            return ResolvedLocation.failed(rawLocation);
        }

        return ResolvedLocation.approximated(
                rawLocation,
                displayName,
                displayLocation,
                place.location().latitude(),
                place.location().longitude(),
                confidence,
                LocationResolvedBy.GOOGLE_PLACES
        );
    }

    private String displayName(GooglePlacesTextSearchResponse.GooglePlace place) {
        if (place.displayName() == null) {
            return null;
        }

        return place.displayName().text();
    }

    private LocationResolutionStatus evaluateStatus(
            String rawLocation,
            GooglePlacesTextSearchResponse.GooglePlace place,
            String displayName
    ) {
        String normalizedRawLocation = normalize(rawLocation);
        String normalizedDisplayName = normalize(displayName);
        String normalizedFormattedAddress = normalize(place.formattedAddress());

        if (!StringUtils.hasText(normalizedRawLocation)) {
            return LocationResolutionStatus.FAILED;
        }

        List<String> informativeTokens = informativeTokens(rawLocation);
        if (informativeTokens.isEmpty()) {
            return LocationResolutionStatus.FAILED;
        }

        boolean hasFormattedAddress = StringUtils.hasText(place.formattedAddress());
        boolean directMatch = isDirectMatch(normalizedRawLocation, normalizedDisplayName, normalizedFormattedAddress);
        Alignment alignment = evaluateAlignment(informativeTokens, normalizedDisplayName, normalizedFormattedAddress);

        boolean hasStrongTokenAlignment = alignment.nameAligned()
                && alignment.addressAligned()
                && alignment.alignedTokenCount() >= 2;
        if (hasFormattedAddress && (directMatch || hasStrongTokenAlignment)) {
            return LocationResolutionStatus.RESOLVED;
        }

        boolean exactSingleTokenNameMatch = alignment.informativeTokenCount() == 1
                && normalizedDisplayName.equals(informativeTokens.getFirst());
        boolean multiTokenPartialMatch = alignment.informativeTokenCount() >= 2
                && alignment.alignedTokenCount() >= 1
                && (alignment.nameAligned() || alignment.addressAligned());
        boolean hasMeaningfulPartialMatch = directMatch
                || exactSingleTokenNameMatch
                || multiTokenPartialMatch;

        if (hasMeaningfulPartialMatch) {
            return LocationResolutionStatus.APPROXIMATED;
        }

        return LocationResolutionStatus.FAILED;
    }

    private boolean isDirectMatch(String normalizedRawLocation, String normalizedDisplayName, String normalizedFormattedAddress) {
        return isExactMatch(normalizedRawLocation, normalizedDisplayName)
                || isExactMatch(normalizedRawLocation, normalizedFormattedAddress);
    }

    private Alignment evaluateAlignment(
            List<String> informativeTokens,
            String normalizedDisplayName,
            String normalizedFormattedAddress
    ) {
        Set<String> alignedTokens = new LinkedHashSet<>();
        boolean nameAligned = false;
        boolean addressAligned = false;

        for (String token : informativeTokens) {
            if (normalizedDisplayName.contains(token)) {
                alignedTokens.add(token);
                nameAligned = true;
            }
            if (normalizedFormattedAddress.contains(token)) {
                alignedTokens.add(token);
                addressAligned = true;
            }
        }

        return new Alignment(nameAligned, addressAligned, alignedTokens.size(), informativeTokens.size());
    }

    private boolean isExactMatch(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }

        return source.equals(target);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.replaceAll("[^0-9a-zA-Z가-힣]", "").toLowerCase();
    }

    private List<String> informativeTokens(String rawLocation) {
        return Arrays.stream(rawLocation.split("\\s+"))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .filter(token -> token.length() >= 2)
                .filter(token -> !isGenericContextToken(token))
                .toList();
    }

    private String normalizePlacesQuery(String rawLocation) {
        List<String> tokens = informativeTokens(rawLocation);
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" ", tokens);
    }

    private boolean isGenericContextToken(String token) {
        return GENERIC_CONTEXT_TOKENS.contains(token) || isTimeToken(token);
    }

    private boolean isTimeToken(String token) {
        return token.matches("\\d+시")
                || token.matches("\\d+시\\d+분")
                || token.matches("\\d+분");
    }

    private record Alignment(
            boolean nameAligned,
            boolean addressAligned,
            int alignedTokenCount,
            int informativeTokenCount
    ) {
    }
}
