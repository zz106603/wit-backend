package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationHomeService recommendationHomeService;

    public RecommendationController(RecommendationHomeService recommendationHomeService) {
        this.recommendationHomeService = recommendationHomeService;
    }

    @GetMapping("/home")
    public HomeRecommendationResponse getHomeRecommendations() {
        List<RecommendationResult> results = recommendationHomeService.getHomeRecommendations();
        return HomeRecommendationResponse.from(results);
    }
}
