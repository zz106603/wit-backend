package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationHomeService recommendationHomeService;

    public RecommendationController(RecommendationHomeService recommendationHomeService) {
        this.recommendationHomeService = recommendationHomeService;
    }

    @GetMapping("/home")
    public HomeRecommendationResponse getHomeRecommendations() {
        log.info("[RecommendationDebug] home request start");
        List<RecommendationResult> results = recommendationHomeService.getHomeRecommendations();
        HomeRecommendationResponse response = HomeRecommendationResponse.from(results);
        log.info("[RecommendationDebug] home request end. recommendationCount={}", results.size());
        return response;
    }
}
