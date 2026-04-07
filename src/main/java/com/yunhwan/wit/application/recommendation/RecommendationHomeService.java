package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecommendationHomeService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationHomeService.class);

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
        List<CalendarEvent> calendarEvents = googleIntegrationService.getUpcomingEvents();
        log.info("[RecommendationDebug] calendar events fetched. count={}", calendarEvents.size());

        return calendarEvents.stream()
                .map(this::recommendSafely)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<RecommendationResult> recommendSafely(CalendarEvent calendarEvent) {
        try {
            log.info(
                    "[RecommendationDebug] event processing start. eventId={}, title={}, rawLocation={}",
                    calendarEvent.eventId(),
                    calendarEvent.title(),
                    calendarEvent.rawLocation()
            );
            RecommendationResult result = recommendationService.recommend(calendarEvent);
            log.info(
                    "[RecommendationDebug] event processing end. eventId={}, weatherFallbackApplied={}",
                    calendarEvent.eventId(),
                    result.weatherFallbackApplied()
            );
            return Optional.of(result);
        } catch (RuntimeException exception) {
            log.warn(
                    "[RecommendationDebug] event processing skip. eventId={}, reason={}",
                    calendarEvent.eventId(),
                    exception.getMessage(),
                    exception
            );
            return Optional.empty();
        }
    }
}
