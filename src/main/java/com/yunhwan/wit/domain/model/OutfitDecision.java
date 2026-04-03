package com.yunhwan.wit.domain.model;

import java.util.Objects;

public record OutfitDecision(
        boolean needUmbrella,
        RecommendedOutfitLevel recommendedOutfitLevel,
        String recommendedOutfitText,
        String umbrellaReason,
        String outfitReason,
        int temperatureGap,
        String weatherChangeSummary,
        String aiSummary
) {

    public OutfitDecision {
        Objects.requireNonNull(recommendedOutfitLevel, "recommendedOutfitLevel must not be null");
        Objects.requireNonNull(recommendedOutfitText, "recommendedOutfitText must not be null");
    }

    public OutfitDecision withAiSummary(String aiSummary) {
        return new OutfitDecision(
                needUmbrella,
                recommendedOutfitLevel,
                recommendedOutfitText,
                umbrellaReason,
                outfitReason,
                temperatureGap,
                weatherChangeSummary,
                aiSummary
        );
    }
}
