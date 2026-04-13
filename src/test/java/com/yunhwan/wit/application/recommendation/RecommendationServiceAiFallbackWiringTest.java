package com.yunhwan.wit.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhwan.wit.application.location.AiLocationFallbackResolver;
import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.application.location.LocationResolutionCache;
import com.yunhwan.wit.application.recommendation.RecommendationCache;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.application.weather.WeatherForecastSnapshots;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.infrastructure.ai.GeminiApiClient;
import com.yunhwan.wit.infrastructure.ai.GeminiGenerateContentRequest;
import com.yunhwan.wit.infrastructure.ai.GeminiGenerateContentResponse;
import com.yunhwan.wit.infrastructure.ai.GeminiLocationResolver;
import com.yunhwan.wit.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class RecommendationServiceAiFallbackWiringTest extends IntegrationTestSupport {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private AiLocationFallbackResolver aiLocationFallbackResolver;

    @MockBean
    private LocationResolutionCache locationResolutionCache;

    @MockBean
    private RecommendationCache recommendationCache;

    @MockBean
    private GooglePlacesLocationResolver googlePlacesLocationResolver;

    @MockBean
    private GeminiApiClient geminiApiClient;

    @MockBean
    private WeatherClient weatherClient;

    @MockBean
    private CurrentLocationProvider currentLocationProvider;

    private CalendarEvent calendarEvent;
    private ResolvedLocation currentLocation;

    @BeforeEach
    void setUp() {
        calendarEvent = new CalendarEvent(
                "event-ai-fallback",
                "회사 회식",
                LocalDateTime.of(2026, 4, 13, 18, 0),
                LocalDateTime.of(2026, 4, 13, 21, 0),
                "회사 회식"
        );
        currentLocation = ResolvedLocation.resolved(
                "현재 위치",
                "current",
                "서울특별시 강남구",
                37.5172,
                127.0473,
                1.0,
                LocationResolvedBy.RULE
        );

        when(locationResolutionCache.find("회사 회식")).thenReturn(Optional.empty());
        when(recommendationCache.find(eq(calendarEvent), any())).thenReturn(Optional.empty());
        when(currentLocationProvider.getCurrentLocation()).thenReturn(currentLocation);
        when(currentLocationProvider.hasRealCurrentLocation()).thenReturn(true);
    }

    @Test
    void 추천_조립경로에서_회사회식은_google_places_실패후_ai_fallback으로_해결된다() {
        when(googlePlacesLocationResolver.resolve("회사 회식"))
                .thenReturn(ResolvedLocation.failed("회사 회식"));
        when(geminiApiClient.generateContent(any(), any(GeminiGenerateContentRequest.class)))
                .thenReturn(geminiResponse("""
                        {
                          "normalizedQuery":"서울특별시 강남구",
                          "displayLocation":"서울특별시 강남구",
                          "lat":37.5172,
                          "lng":127.0473,
                          "confidence":0.82,
                          "status":"RESOLVED"
                        }
                        """))
                .thenReturn(geminiResponse("""
                        회사 회식은 강남 지역 일정으로 보고 우산을 챙기고 긴팔 + 가벼운 겉옷 차림을 추천합니다.
                        """));

        WeatherSnapshot currentWeather = snapshot(currentLocation, LocalDateTime.of(2026, 4, 13, 9, 0), 23, 23, 10, WeatherType.CLEAR);
        WeatherSnapshot startWeather = snapshot("서울특별시 강남구", calendarEvent.startAt(), 21, 21, 20, WeatherType.CLOUDY);
        WeatherSnapshot endWeather = snapshot("서울특별시 강남구", calendarEvent.endAt(), 18, 18, 60, WeatherType.RAIN);

        when(weatherClient.fetchCurrentWeather(currentLocation)).thenReturn(currentWeather);
        when(weatherClient.fetchWeatherRange(
                argThat(location -> location != null
                        && location.status() == LocationResolutionStatus.RESOLVED
                        && location.resolvedBy() == LocationResolvedBy.AI
                        && "회사 회식".equals(location.rawLocation())
                        && "서울특별시 강남구".equals(location.displayLocation())),
                eq(calendarEvent.startAt()),
                eq(calendarEvent.endAt())
        )).thenReturn(new WeatherForecastSnapshots(startWeather, endWeather));

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(aiLocationFallbackResolver).isInstanceOf(GeminiLocationResolver.class);
        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.resolvedLocation().rawLocation()).isEqualTo("회사 회식");
        assertThat(result.resolvedLocation().status()).isEqualTo(LocationResolutionStatus.RESOLVED);
        assertThat(result.resolvedLocation().resolvedBy()).isEqualTo(LocationResolvedBy.AI);
        assertThat(result.resolvedLocation().displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.outfitDecision().aiSummary()).contains("회사 회식");

        verify(googlePlacesLocationResolver).resolve("회사 회식");
        verify(geminiApiClient, atLeastOnce()).generateContent(any(), any(GeminiGenerateContentRequest.class));
    }

    private GeminiGenerateContentResponse geminiResponse(String jsonText) {
        return new GeminiGenerateContentResponse(List.of(
                new GeminiGenerateContentResponse.Candidate(
                        new GeminiGenerateContentResponse.Content(
                                List.of(new GeminiGenerateContentResponse.Part(jsonText))
                        )
                )
        ));
    }

    private WeatherSnapshot snapshot(
            ResolvedLocation location,
            LocalDateTime targetTime,
            int temperature,
            int feelsLike,
            int precipitationProbability,
            WeatherType weatherType
    ) {
        return snapshot(
                location.displayLocation(),
                targetTime,
                temperature,
                feelsLike,
                precipitationProbability,
                weatherType
        );
    }

    private WeatherSnapshot snapshot(
            String regionName,
            LocalDateTime targetTime,
            int temperature,
            int feelsLike,
            int precipitationProbability,
            WeatherType weatherType
    ) {
        return new WeatherSnapshot(
                regionName,
                targetTime,
                temperature,
                feelsLike,
                precipitationProbability,
                weatherType
        );
    }
}
