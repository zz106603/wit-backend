package com.yunhwan.wit.presentation.api.recommendation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.infrastructure.config.SecurityConfig;
import com.yunhwan.wit.presentation.api.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationHomeService recommendationHomeService;

    @Test
    void home_추천_응답은_로컬검증에_필요한_필드를_반환한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(recommendationResult()));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.recommendations[0].title").value("강남 회식"))
                .andExpect(jsonPath("$.recommendations[0].rawLocation").value("강남 목구멍"))
                .andExpect(jsonPath("$.recommendations[0].resolvedLocation.displayLocation")
                        .value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.recommendations[0].resolvedLocation.resolvedBy").value("GOOGLE_PLACES"))
                .andExpect(jsonPath("$.recommendations[0].needUmbrella").value(true))
                .andExpect(jsonPath("$.recommendations[0].recommendedOutfitText").value("긴팔 + 가벼운 겉옷"))
                .andExpect(jsonPath("$.recommendations[0].aiSummary").value("종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다."))
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].endWeather.weatherType").value("RAIN"));
    }

    @Test
    void weather_fallback_응답은_weather_snapshot을_null로_반환한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(fallbackRecommendationResult()));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(true))
                .andExpect(jsonPath("$.recommendations[0].currentWeather").value(nullValue()))
                .andExpect(jsonPath("$.recommendations[0].startWeather").value(nullValue()))
                .andExpect(jsonPath("$.recommendations[0].endWeather").value(nullValue()));
    }

    private RecommendationResult recommendationResult() {
        CalendarEvent event = calendarEvent();
        ResolvedLocation location = location(event);
        OutfitDecision decision = decision();

        return new RecommendationResult(
                decision,
                event,
                location,
                weatherSnapshot(location, LocalDateTime.of(2026, 4, 7, 9, 0), 20, 20, 10, WeatherType.CLEAR),
                weatherSnapshot(location, event.startAt(), 19, 18, 30, WeatherType.CLOUDY),
                weatherSnapshot(location, event.endAt(), 16, 14, 70, WeatherType.RAIN),
                false
        );
    }

    private RecommendationResult fallbackRecommendationResult() {
        CalendarEvent event = calendarEvent();
        return new RecommendationResult(
                decision(),
                event,
                location(event),
                null,
                null,
                null,
                true
        );
    }

    private CalendarEvent calendarEvent() {
        return new CalendarEvent(
                "event-1",
                "강남 회식",
                LocalDateTime.of(2026, 4, 7, 18, 0),
                LocalDateTime.of(2026, 4, 7, 21, 0),
                "강남 목구멍"
        );
    }

    private ResolvedLocation location(CalendarEvent event) {
        return ResolvedLocation.resolved(
                event.rawLocation(),
                "목구멍 강남점",
                "서울특별시 강남구 테헤란로 1",
                37.5001,
                127.0362,
                0.85,
                LocationResolvedBy.GOOGLE_PLACES
        );
    }

    private OutfitDecision decision() {
        return new OutfitDecision(
                true,
                RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER,
                "긴팔 + 가벼운 겉옷",
                "종료 시점 비 예보가 있어 우산이 필요합니다.",
                "종료 시점 체감온도 기준으로 긴팔 + 가벼운 겉옷을 추천합니다.",
                -6,
                "현재보다 종료 시점이 더 쌀쌀합니다.",
                "종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다."
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
