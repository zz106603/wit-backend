package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "홈 추천 목록 응답")
public record HomeRecommendationResponse(
        @Schema(description = "추천 목록")
        List<RecommendationResponse> recommendations
) {

    public static HomeRecommendationResponse from(List<RecommendationResult> results) {
        return new HomeRecommendationResponse(results.stream()
                .map(RecommendationResponse::from)
                .toList());
    }
}
