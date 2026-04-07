package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RecommendationHomeService {

    private final GoogleIntegrationService googleIntegrationService;
    private final RecommendationService recommendationService;

    public RecommendationHomeService(
            GoogleIntegrationService googleIntegrationService,
            RecommendationService recommendationService
    ) {
        this.googleIntegrationService = Objects.requireNonNull(
                googleIntegrationService,
                "googleIntegrationService must not be null"
        );
        this.recommendationService = Objects.requireNonNull(
                recommendationService,
                "recommendationService must not be null"
        );
    }

    public List<RecommendationResult> getHomeRecommendations() {
        return googleIntegrationService.getUpcomingEvents().stream()
                .map(this::recommendSafely)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<RecommendationResult> recommendSafely(CalendarEvent calendarEvent) {
        try {
            return Optional.of(recommendationService.recommend(calendarEvent));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
