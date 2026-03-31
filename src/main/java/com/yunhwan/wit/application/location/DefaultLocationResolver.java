package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Objects;

public class DefaultLocationResolver implements LocationResolver {

    private final RuleBasedLocationResolver ruleBasedLocationResolver;
    private final AiLocationFallbackResolver aiLocationFallbackResolver;

    public DefaultLocationResolver(
            RuleBasedLocationResolver ruleBasedLocationResolver,
            AiLocationFallbackResolver aiLocationFallbackResolver
    ) {
        this.ruleBasedLocationResolver = Objects.requireNonNull(
                ruleBasedLocationResolver,
                "ruleBasedLocationResolver must not be null"
        );
        this.aiLocationFallbackResolver = Objects.requireNonNull(
                aiLocationFallbackResolver,
                "aiLocationFallbackResolver must not be null"
        );
    }

    @Override
    public ResolvedLocation resolve(String rawLocation) {
        ResolvedLocation ruleResult = ruleBasedLocationResolver.resolve(rawLocation);

        if (ruleResult.status() == LocationResolutionStatus.RESOLVED) {
            return ruleResult;
        }

        if (isMeaninglessInput(rawLocation)) {
            return ruleResult;
        }

        ResolvedLocation aiResult = aiLocationFallbackResolver.resolve(rawLocation);
        if (isSuccessfulAiResult(aiResult, rawLocation)) {
            return aiResult;
        }

        return ruleResult;
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
