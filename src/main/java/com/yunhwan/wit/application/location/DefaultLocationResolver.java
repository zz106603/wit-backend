package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLocationResolver implements LocationResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultLocationResolver.class);
    private static final String LOG_PREFIX = "[LocationResolverFlow]";
    private static final double SUFFICIENT_CONFIDENCE_THRESHOLD = 0.8;

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
                "{} rawLocation={}, step=RULE, status={}, resolvedBy={}, displayLocation={}",
                LOG_PREFIX,
                rawLocation,
                ruleResult.status(),
                ruleResult.resolvedBy(),
                ruleResult.displayLocation()
        );

        if (isMeaninglessInput(rawLocation)) {
            log.info(
                    "{} rawLocation={}, step=RULE, status={}, result=FAILED, reason=meaningless-input",
                    LOG_PREFIX,
                    rawLocation,
                    ruleResult.status()
            );
            return ruleResult;
        }

        if (isSufficientResolution(ruleResult, rawLocation, LocationResolvedBy.RULE)) {
            log.info(
                    "{} rawLocation={}, step=RULE, status={}, result=USED, displayLocation={}",
                    LOG_PREFIX,
                    rawLocation,
                    ruleResult.status(),
                    ruleResult.displayLocation()
            );
            return ruleResult;
        }

        ResolvedLocation googlePlacesResult = resolveByGooglePlaces(rawLocation);
        if (isAcceptedGooglePlacesResolution(googlePlacesResult, rawLocation)) {
            log.info(
                    "{} rawLocation={}, step=PLACES, status={}, result=USED, displayLocation={}",
                    LOG_PREFIX,
                    rawLocation,
                    googlePlacesResult.status(),
                    googlePlacesResult.displayLocation()
            );
            return googlePlacesResult;
        }

        log.info(
                "{} rawLocation={}, step=PLACES, status={}, result=INSUFFICIENT, nextStep=AI",
                LOG_PREFIX,
                rawLocation,
                googlePlacesResult.status()
        );
        ResolvedLocation aiResult = resolveByAi(rawLocation);
        log.info(
                "{} rawLocation={}, step=AI, status={}, resolvedBy={}",
                LOG_PREFIX,
                rawLocation,
                aiResult.status(),
                aiResult.resolvedBy()
        );
        if (isSuccessfulAiResult(aiResult, rawLocation)) {
            log.info(
                    "{} rawLocation={}, step=AI, status={}, result=USED, displayLocation={}",
                    LOG_PREFIX,
                    rawLocation,
                    aiResult.status(),
                    aiResult.displayLocation()
            );
            return aiResult;
        }

        log.info(
                "{} rawLocation={}, step=AI, status={}, result=FAILED, reason=all-resolvers-failed",
                LOG_PREFIX,
                rawLocation,
                aiResult.status()
        );
        return ResolvedLocation.failed(rawLocation);
    }

    private ResolvedLocation resolveByGooglePlaces(String rawLocation) {
        try {
            log.info("{} rawLocation={}, step=PLACES, action=INVOKE", LOG_PREFIX, rawLocation);
            ResolvedLocation result = googlePlacesLocationResolver.resolve(rawLocation);
            log.info(
                    "{} rawLocation={}, step=PLACES, status={}, resolvedBy={}",
                    LOG_PREFIX,
                    rawLocation,
                    result.status(),
                    result.resolvedBy()
            );
            return result;
        } catch (RuntimeException exception) {
            log.warn("{} rawLocation={}, step=PLACES, status=FAILED, reason=exception", LOG_PREFIX, rawLocation, exception);
            return ResolvedLocation.failed(rawLocation);
        }
    }

    private ResolvedLocation resolveByAi(String rawLocation) {
        try {
            log.info("{} rawLocation={}, step=AI, action=INVOKE", LOG_PREFIX, rawLocation);
            return aiLocationFallbackResolver.resolve(rawLocation);
        } catch (RuntimeException exception) {
            log.info("{} rawLocation={}, step=AI, status=FAILED, result=RETURNED, reason=exception-caught", LOG_PREFIX, rawLocation);
            return ResolvedLocation.failed(rawLocation);
        }
    }

    private boolean isSufficientResolution(
            ResolvedLocation result,
            String rawLocation,
            LocationResolvedBy expectedResolvedBy
    ) {
        if (result == null) {
            return false;
        }

        if (result.status() != LocationResolutionStatus.RESOLVED) {
            return false;
        }

        if (result.resolvedBy() != expectedResolvedBy) {
            return false;
        }

        if (!Objects.equals(result.rawLocation(), rawLocation)) {
            return false;
        }

        return result.confidence() >= SUFFICIENT_CONFIDENCE_THRESHOLD;
    }

    private boolean isAcceptedGooglePlacesResolution(ResolvedLocation result, String rawLocation) {
        if (result == null) {
            return false;
        }

        if (result.resolvedBy() != LocationResolvedBy.GOOGLE_PLACES) {
            return false;
        }

        if (!Objects.equals(result.rawLocation(), rawLocation)) {
            return false;
        }

        if (result.status() == LocationResolutionStatus.RESOLVED) {
            return result.confidence() >= SUFFICIENT_CONFIDENCE_THRESHOLD;
        }

        return result.status() == LocationResolutionStatus.APPROXIMATED;
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
