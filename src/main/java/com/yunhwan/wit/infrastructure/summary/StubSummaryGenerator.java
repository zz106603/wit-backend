package com.yunhwan.wit.infrastructure.summary;

import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.domain.model.OutfitDecision;
import java.util.Objects;

public class StubSummaryGenerator implements SummaryGenerator {

    @Override
    public String generate(OutfitDecision outfitDecision) {
        Objects.requireNonNull(outfitDecision, "outfitDecision must not be null");

        String umbrellaText = outfitDecision.needUmbrella() ? "우산을 챙기고" : "우산 없이";
        return umbrellaText + " " + outfitDecision.recommendedOutfitText() + " 차림을 추천합니다.";
    }
}
