package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "홈 추천 목록 응답")
public record HomeRecommendationResponse(
        @Schema(
                description = "추천 목록. 홈 API는 각 일정 추천을 독립적으로 생성하며, 일부 일정에서 추천 생성에 실패하면 해당 항목은 목록에서 제외될 수 있다."
        )
        List<RecommendationResponse> recommendations
) {

    public static HomeRecommendationResponse from(List<RecommendationResult> results) {
        return new HomeRecommendationResponse(results.stream()
                .map(RecommendationResponse::from)
                .toList());
    }
}
