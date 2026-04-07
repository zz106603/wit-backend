package com.yunhwan.wit.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationHomeServiceTest {

    private final GoogleIntegrationService googleIntegrationService = mock(GoogleIntegrationService.class);
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final RecommendationHomeService homeService = new RecommendationHomeService(
            googleIntegrationService,
            recommendationService
    );

    @Test
    void upcoming_events를_추천결과로_조립한다() {
        CalendarEvent event = calendarEvent("event-1");
        RecommendationResult recommendationResult = recommendationResult(event);
        given(googleIntegrationService.getUpcomingEvents()).willReturn(List.of(event));
        given(recommendationService.recommend(event)).willReturn(recommendationResult);

        List<RecommendationResult> results = homeService.getHomeRecommendations();

        assertThat(results).containsExactly(recommendationResult);
    }

    @Test
    void 일부_일정_추천이_실패하면_해당_일정만_건너뛴다() {
        CalendarEvent failingEvent = calendarEvent("event-1");
        CalendarEvent successfulEvent = calendarEvent("event-2");
        RecommendationResult recommendationResult = recommendationResult(successfulEvent);
        given(googleIntegrationService.getUpcomingEvents()).willReturn(List.of(failingEvent, successfulEvent));
        given(recommendationService.recommend(failingEvent)).willThrow(new RuntimeException("recommendation failed"));
        given(recommendationService.recommend(successfulEvent)).willReturn(recommendationResult);

        List<RecommendationResult> results = homeService.getHomeRecommendations();

        assertThat(results).containsExactly(recommendationResult);
    }

    private CalendarEvent calendarEvent(String eventId) {
        return new CalendarEvent(
                eventId,
                "강남 회식",
                LocalDateTime.of(2026, 4, 7, 18, 0),
                LocalDateTime.of(2026, 4, 7, 21, 0),
                "강남 목구멍"
        );
    }

    private RecommendationResult recommendationResult(CalendarEvent event) {
        ResolvedLocation location = ResolvedLocation.resolved(
                event.rawLocation(),
                "목구멍 강남점",
                "서울특별시 강남구 테헤란로 1",
                37.5001,
                127.0362,
                0.85,
                LocationResolvedBy.GOOGLE_PLACES
        );
        WeatherSnapshot currentWeather = weatherSnapshot(location, LocalDateTime.of(2026, 4, 7, 9, 0), 20, 20, 10, WeatherType.CLEAR);
        WeatherSnapshot startWeather = weatherSnapshot(location, event.startAt(), 19, 18, 30, WeatherType.CLOUDY);
        WeatherSnapshot endWeather = weatherSnapshot(location, event.endAt(), 16, 14, 70, WeatherType.RAIN);
        OutfitDecision outfitDecision = new OutfitDecision(
                true,
                RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER,
                "긴팔 + 가벼운 겉옷",
                "종료 시점 비 예보가 있어 우산이 필요합니다.",
                "종료 시점 체감온도 기준으로 긴팔 + 가벼운 겉옷을 추천합니다.",
                -6,
                "현재보다 종료 시점이 더 쌀쌀합니다.",
                null
        );

        return new RecommendationResult(
                outfitDecision,
                event,
                location,
                currentWeather,
                startWeather,
                endWeather,
                false
        );
    }

    private WeatherSnapshot weatherSnapshot(
            ResolvedLocation location,
            LocalDateTime targetTime,
            int temperature,
            int feelsLike,
            int precipitationProbability,
            WeatherType weatherType
    ) {
        return new WeatherSnapshot(
                location.displayLocation(),
                targetTime,
                temperature,
                feelsLike,
                precipitationProbability,
                weatherType
        );
    }
}
