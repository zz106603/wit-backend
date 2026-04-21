package com.yunhwan.wit.presentation.api.recommendation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.application.recommendation.RecommendationWeatherSource;
import com.yunhwan.wit.application.exception.ErrorCode;
import com.yunhwan.wit.application.exception.WitException;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.infrastructure.config.SecurityConfig;
import com.yunhwan.wit.presentation.api.GlobalExceptionHandler;
import com.yunhwan.wit.presentation.filter.TraceIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TraceIdFilter.class})
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationHomeService recommendationHomeService;

    @Test
    void home_추천_응답은_로컬검증에_필요한_필드를_반환한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(recommendationResult()));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.recommendations[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.recommendations[0].title").value("강남 회식"))
                .andExpect(jsonPath("$.recommendations[0].startAt").value("2026-04-07T18:00:00"))
                .andExpect(jsonPath("$.recommendations[0].endAt").value("2026-04-07T21:00:00"))
                .andExpect(jsonPath("$.recommendations[0].location").value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.recommendations[0].rawLocation").value("강남 목구멍"))
                .andExpect(jsonPath("$.recommendations[0].locationResolution.displayLocation")
                        .value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.recommendations[0].locationResolution.resolvedBy").value("GOOGLE_PLACES"))
                .andExpect(jsonPath("$.recommendations[0].originalLocationResolution").value(nullValue()))
                .andExpect(jsonPath("$.recommendations[0].locationFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("NORMAL"))
                .andExpect(jsonPath("$.recommendations[0].needUmbrella").value(true))
                .andExpect(jsonPath("$.recommendations[0].recommendedOutfitText").value("긴팔 + 가벼운 겉옷"))
                .andExpect(jsonPath("$.recommendations[0].summary").value("종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다."))
                .andExpect(jsonPath("$.recommendations[0].fallbackNotice").value(nullValue()))
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
                .andExpect(jsonPath("$.recommendations[0].locationFallbackApplied").value(true))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("SAFE_DEFAULT"))
                .andExpect(jsonPath("$.recommendations[0].fallbackNotice").value("날씨 조회 실패로 안전 기본 추천을 반환했습니다."))
                .andExpect(jsonPath("$.recommendations[0].originalLocationResolution.status").value("FAILED"))
                .andExpect(jsonPath("$.recommendations[0].originalLocationResolution.rawLocation").value("강남 목구멍"))
                .andExpect(jsonPath("$.recommendations[0].currentWeather").value(nullValue()))
                .andExpect(jsonPath("$.recommendations[0].startWeather").value(nullValue()))
                .andExpect(jsonPath("$.recommendations[0].endWeather").value(nullValue()));
    }

    @Test
    void cache기반_추천응답은_weather_source를_CACHE로_반환한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(cachedRecommendationResult()));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("CACHE"))
                .andExpect(jsonPath("$.recommendations[0].fallbackNotice").value("실시간 호출 대신 캐시된 날씨 데이터를 사용했습니다."));
    }

    @Test
    void 이벤트_상세_추천_응답은_단건_상세를_반환한다() throws Exception {
        given(recommendationHomeService.getEventRecommendation("event-1"))
                .willReturn(recommendationResult());

        mockMvc.perform(get("/api/recommendations/events/event-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("event-1"))
                .andExpect(jsonPath("$.title").value("강남 회식"))
                .andExpect(jsonPath("$.location").value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.originalLocationResolution").value(nullValue()))
                .andExpect(jsonPath("$.summary").value("종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다."))
                .andExpect(jsonPath("$.endWeather.weatherType").value("RAIN"));
    }

    @Test
    void 이벤트_상세_추천_대상이_없으면_404를_반환한다() throws Exception {
        given(recommendationHomeService.getEventRecommendation("event-404"))
                .willThrow(new WitException(
                        ErrorCode.RECOMMENDATION_EVENT_NOT_FOUND,
                        "eventId에 해당하는 추천 대상이 없습니다. eventId=event-404"
                ));

        mockMvc.perform(get("/api/recommendations/events/event-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_404"))
                .andExpect(jsonPath("$.message").value("eventId에 해당하는 추천 대상이 없습니다. eventId=event-404"));
    }

    @Test
    void 요청에_trace_id가_있으면_응답헤더에_같은값을_유지한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(recommendationResult()));

        mockMvc.perform(get("/api/recommendations/home")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "trace-id-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "trace-id-123"));
    }

    @Test
    void 요청_trace_id가_유효하지_않으면_새_trace_id를_반환한다() throws Exception {
        given(recommendationHomeService.getHomeRecommendations())
                .willReturn(List.of(recommendationResult()));

        mockMvc.perform(get("/api/recommendations/home")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "invalid_trace_id"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, org.hamcrest.Matchers.not("invalid_trace_id")));
    }

    private RecommendationResult recommendationResult() {
        CalendarEvent event = calendarEvent();
        ResolvedLocation location = location(event);
        OutfitDecision decision = decision();

        return new RecommendationResult(
                decision,
                event,
                location,
                null,
                weatherSnapshot(location, LocalDateTime.of(2026, 4, 7, 9, 0), 20, 20, 10, WeatherType.CLEAR),
                weatherSnapshot(location, event.startAt(), 19, 18, 30, WeatherType.CLOUDY),
                weatherSnapshot(location, event.endAt(), 16, 14, 70, WeatherType.RAIN),
                false,
                false,
                RecommendationWeatherSource.NORMAL
        );
    }

    private RecommendationResult fallbackRecommendationResult() {
        CalendarEvent event = calendarEvent();
        return new RecommendationResult(
                decision(),
                event,
                location(event),
                ResolvedLocation.failed(event.rawLocation()),
                null,
                null,
                null,
                true,
                true,
                RecommendationWeatherSource.SAFE_DEFAULT
        );
    }

    private RecommendationResult cachedRecommendationResult() {
        CalendarEvent event = calendarEvent();
        ResolvedLocation location = location(event);
        return new RecommendationResult(
                decision(),
                event,
                location,
                null,
                weatherSnapshot(location, LocalDateTime.of(2026, 4, 7, 9, 0), 20, 20, 10, WeatherType.CLEAR),
                weatherSnapshot(location, event.startAt(), 19, 18, 30, WeatherType.CLOUDY),
                weatherSnapshot(location, event.endAt(), 16, 14, 70, WeatherType.RAIN),
                false,
                false,
                RecommendationWeatherSource.CACHE
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
