package com.yunhwan.wit.domain.rule;

import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;

public class WeatherFailureFallbackDecisionProvider {

    public OutfitDecision provide() {
        return new OutfitDecision(
                true,
                RecommendedOutfitLevel.HEAVY_OUTER,
                "두꺼운 겉옷",
                "날씨 정보를 확인할 수 없어 우산을 챙기는 보수적 추천을 제공합니다.",
                "날씨 정보를 가져오지 못해 안전 기준으로 두꺼운 겉옷을 추천합니다.",
                0,
                "날씨 정보를 가져오지 못해 안전 기본 추천으로 대체되었습니다.",
                null
        );
    }
}
