package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLocationResolver implements LocationResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultLocationResolver.class);

    private final RuleBasedLocationResolver ruleBasedLocationResolver;
    private final GooglePlacesLocationResolver googlePlacesLocationResolver;
    private final AiLocationFallbackResolver aiLocationFallbackResolver;

    public DefaultLocationResolver(
            RuleBasedLocationResolver ruleBasedLocationResolver,
            GooglePlacesLocationResolver googlePlacesLocationResolver,
            AiLocationFallbackResolver aiLocationFallbackResolver
    ) {
        this.ruleBasedLocationResolver = Objects.requireNonNull(
                ruleBasedLocationResolver,
                "ruleBasedLocationResolver must not be null"
        );
        this.googlePlacesLocationResolver = Objects.requireNonNull(
                googlePlacesLocationResolver,
                "googlePlacesLocationResolver must not be null"
        );
        this.aiLocationFallbackResolver = Objects.requireNonNull(
                aiLocationFallbackResolver,
                "aiLocationFallbackResolver must not be null"
        );
    }

    @Override
    public ResolvedLocation resolve(String rawLocation) {
        ResolvedLocation ruleResult = ruleBasedLocationResolver.resolve(rawLocation);
        log.info(
                "[RecommendationDebug] location rule result. rawLocation={}, status={}, resolvedBy={}, displayLocation={}",
                rawLocation,
                ruleResult.status(),
                ruleResult.resolvedBy(),
                ruleResult.displayLocation()
        );

        if (ruleResult.status() == LocationResolutionStatus.RESOLVED) {
            return ruleResult;
        }

        if (isMeaninglessInput(rawLocation)) {
            return ruleResult;
        }

        ResolvedLocation googlePlacesResult = resolveByGooglePlaces(rawLocation);
        if (isSuccessfulGooglePlacesResult(googlePlacesResult, rawLocation)) {
            return googlePlacesResult;
        }

        log.info("[RecommendationDebug] AI fallback before. rawLocation={}", rawLocation);
        ResolvedLocation aiResult = aiLocationFallbackResolver.resolve(rawLocation);
        log.info(
                "[RecommendationDebug] AI fallback after. rawLocation={}, status={}, resolvedBy={}",
                rawLocation,
                aiResult.status(),
                aiResult.resolvedBy()
        );
        if (isSuccessfulAiResult(aiResult, rawLocation)) {
            return aiResult;
        }

        return ruleResult;
    }

    private ResolvedLocation resolveByGooglePlaces(String rawLocation) {
        try {
            log.info("[RecommendationDebug] Google Places before. rawLocation={}", rawLocation);
            ResolvedLocation result = googlePlacesLocationResolver.resolve(rawLocation);
            log.info(
                    "[RecommendationDebug] Google Places after. rawLocation={}, status={}, resolvedBy={}",
                    rawLocation,
                    result.status(),
                    result.resolvedBy()
            );
            return result;
        } catch (RuntimeException exception) {
            log.warn("[RecommendationDebug] Google Places failed. rawLocation={}", rawLocation, exception);
            return ResolvedLocation.failed(rawLocation);
        }
    }

    private boolean isSuccessfulGooglePlacesResult(ResolvedLocation googlePlacesResult, String rawLocation) {
        if (googlePlacesResult == null) {
            return false;
        }

        if (googlePlacesResult.status() == LocationResolutionStatus.FAILED) {
            return false;
        }

        if (googlePlacesResult.resolvedBy() != LocationResolvedBy.GOOGLE_PLACES) {
            return false;
        }

        return Objects.equals(googlePlacesResult.rawLocation(), rawLocation);
    }

    private boolean isSuccessfulAiResult(ResolvedLocation aiResult, String rawLocation) {
        if (aiResult == null) {
            return false;
        }

        if (aiResult.status() == LocationResolutionStatus.FAILED) {
            return false;
        }

        if (aiResult.resolvedBy() != LocationResolvedBy.AI) {
            return false;
        }

        return Objects.equals(aiResult.rawLocation(), rawLocation);
    }

    private boolean isMeaninglessInput(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return true;
        }

        return rawLocation.replaceAll("[^0-9a-zA-Z가-힣]", "").isBlank();
    }
}
