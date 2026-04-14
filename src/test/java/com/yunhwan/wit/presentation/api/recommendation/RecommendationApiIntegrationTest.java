package com.yunhwan.wit.presentation.api.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhwan.wit.application.google.GoogleCalendarClient;
import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.application.google.GoogleIntegrationRepository;
import com.yunhwan.wit.application.google.GoogleIntegrationUnavailableException;
import com.yunhwan.wit.application.google.GoogleIntegrationUserProvider;
import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.application.google.GoogleReauthenticationRequiredException;
import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.application.location.LocationResolutionCache;
import com.yunhwan.wit.application.recommendation.RecommendationCache;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherCache;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.application.weather.WeatherForecastSnapshots;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.infrastructure.ai.GeminiApiClient;
import com.yunhwan.wit.infrastructure.weather.HttpWeatherClient;
import com.yunhwan.wit.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RecommendationApiIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID = "integration-test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoogleIntegrationRepository googleIntegrationRepository;

    @MockitoBean
    private GoogleIntegrationUserProvider googleIntegrationUserProvider;

    @MockitoBean
    private GoogleCalendarClient googleCalendarClient;

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @MockitoBean
    private LocationResolutionCache locationResolutionCache;

    @MockitoBean
    private RecommendationCache recommendationCache;

    @MockitoBean
    private GooglePlacesLocationResolver googlePlacesLocationResolver;

    @MockitoBean
    private GeminiApiClient geminiApiClient;

    @MockitoBean
    private CurrentLocationProvider currentLocationProvider;

    @MockitoBean
    private HttpWeatherClient httpWeatherClient;

    @MockitoBean
    private WeatherCache weatherCache;

    @MockitoBean(name = "summaryGenerator")
    private SummaryGenerator summaryGenerator;

    @BeforeEach
    void setUp() {
        given(googleIntegrationUserProvider.getCurrentUserId()).willReturn(USER_ID);
        googleIntegrationRepository.save(activeIntegration());

        given(locationResolutionCache.find(anyString())).willReturn(Optional.empty());
        given(recommendationCache.find(any(CalendarEvent.class), any(LocalDateTime.class))).willReturn(Optional.empty());

        given(weatherCache.findCurrent(any(ResolvedLocation.class), any(LocalDateTime.class))).willReturn(Optional.empty());
        given(weatherCache.findForecast(any(ResolvedLocation.class), any(LocalDateTime.class))).willReturn(Optional.empty());
        given(weatherCache.findLatestCurrent(any(ResolvedLocation.class))).willReturn(Optional.empty());
        given(weatherCache.findLatestForecast(any(ResolvedLocation.class))).willReturn(Optional.empty());

        given(currentLocationProvider.getCurrentLocation()).willReturn(currentLocation());
        given(currentLocationProvider.hasRealCurrentLocation()).willReturn(true);
        given(summaryGenerator.generate(any())).willReturn("요약 문구");
    }

    @Test
    void 홈_추천_API_정상_흐름을_검증한다() throws Exception {
        CalendarEvent event = event("event-1", "강남 회식", "강남 목구멍");
        ResolvedLocation resolvedLocation = resolvedLocation(event.rawLocation());

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation())).willReturn(resolvedLocation);
        stubNormalWeather(resolvedLocation, event);
        given(summaryGenerator.generate(any())).willReturn("종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다.");

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.recommendations[0].title").value("강남 회식"))
                .andExpect(jsonPath("$.recommendations[0].location").value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.recommendations[0].needUmbrella").value(true))
                .andExpect(jsonPath("$.recommendations[0].recommendedOutfitText").value("두꺼운 겉옷"))
                .andExpect(jsonPath("$.recommendations[0].summary").value("종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다."))
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].locationFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("NORMAL"));
    }

    @Test
    void 이벤트_상세_API_정상_흐름을_검증한다() throws Exception {
        CalendarEvent event = event("event-detail", "판교 미팅", "판교역");
        ResolvedLocation resolvedLocation = ResolvedLocation.resolved(
                event.rawLocation(),
                "판교역",
                "경기도 성남시 분당구 판교역로 160",
                37.3948,
                127.1112,
                0.90,
                LocationResolvedBy.GOOGLE_PLACES
        );

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation())).willReturn(resolvedLocation);
        stubNormalWeather(resolvedLocation, event);
        given(summaryGenerator.generate(any())).willReturn("판교 일정은 긴팔 + 가벼운 겉옷 차림이 적절합니다.");

        mockMvc.perform(get("/api/recommendations/events/event-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("event-detail"))
                .andExpect(jsonPath("$.title").value("판교 미팅"))
                .andExpect(jsonPath("$.location").value("경기도 성남시 분당구 판교역로 160"))
                .andExpect(jsonPath("$.summary").value("판교 일정은 긴팔 + 가벼운 겉옷 차림이 적절합니다."));
    }

    @Test
    void 위치_해석_실패시_현재위치로_fallback한다() throws Exception {
        CalendarEvent event = event("event-location-fallback", "회사 회식", "회사 회식");
        ResolvedLocation currentLocation = currentLocation();

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation()))
                .willReturn(ResolvedLocation.failed(event.rawLocation()));
        given(geminiApiClient.generateContent(anyString(), any())).willThrow(new RuntimeException("ai failed"));
        stubNormalWeather(currentLocation, event);
        given(summaryGenerator.generate(any())).willReturn("현재 위치 기준으로 우산과 긴팔 + 가벼운 겉옷을 준비하면 됩니다.");

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].locationFallbackApplied").value(true))
                .andExpect(jsonPath("$.recommendations[0].location").value("서울특별시 강남구"))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("NORMAL"));
    }

    @Test
    void 날씨_실패시_최신_캐시로_fallback한다() throws Exception {
        CalendarEvent event = event("event-weather-cache", "강남 회식", "강남 목구멍");
        ResolvedLocation resolvedLocation = resolvedLocation(event.rawLocation());
        WeatherSnapshot latestCurrent = weatherSnapshot(
                currentLocation().displayLocation(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                20,
                20,
                10,
                WeatherType.CLEAR
        );
        WeatherSnapshot latestForecast = weatherSnapshot(
                resolvedLocation.displayLocation(),
                LocalDateTime.of(2026, 4, 7, 21, 0),
                16,
                14,
                70,
                WeatherType.RAIN
        );

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation())).willReturn(resolvedLocation);
        given(httpWeatherClient.fetchCurrentWeatherResult(any(ResolvedLocation.class)))
                .willThrow(new RuntimeException("weather current failed"));
        given(httpWeatherClient.fetchWeatherRangeResult(any(ResolvedLocation.class), eq(event.startAt()), eq(event.endAt())))
                .willThrow(new RuntimeException("weather range failed"));
        given(weatherCache.findLatestCurrent(any(ResolvedLocation.class))).willReturn(Optional.of(latestCurrent));
        given(weatherCache.findLatestForecast(any(ResolvedLocation.class))).willReturn(Optional.of(latestForecast));
        given(summaryGenerator.generate(any())).willReturn("실시간 날씨 대신 최신 캐시 데이터로 추천합니다.");

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(false))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("CACHE"))
                .andExpect(jsonPath("$.recommendations[0].fallbackNotice").value("실시간 날씨 대신 최신 캐시 데이터를 사용했습니다."));
    }

    @Test
    void 캐시도_없으면_safe_default로_fallback한다() throws Exception {
        CalendarEvent event = event("event-safe-default", "강남 회식", "강남 목구멍");
        ResolvedLocation resolvedLocation = resolvedLocation(event.rawLocation());

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation())).willReturn(resolvedLocation);
        given(httpWeatherClient.fetchCurrentWeatherResult(any(ResolvedLocation.class)))
                .willThrow(new RuntimeException("weather current failed"));
        given(httpWeatherClient.fetchWeatherRangeResult(any(ResolvedLocation.class), eq(event.startAt()), eq(event.endAt())))
                .willThrow(new RuntimeException("weather range failed"));
        given(summaryGenerator.generate(any())).willReturn("날씨 조회 실패로 안전 기본 추천을 반환했습니다.");

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].weatherFallbackApplied").value(true))
                .andExpect(jsonPath("$.recommendations[0].weatherSource").value("SAFE_DEFAULT"))
                .andExpect(jsonPath("$.recommendations[0].fallbackNotice").value("날씨 조회 실패로 안전 기본 추천을 반환했습니다."))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"currentWeather\":null")));
    }

    @Test
    void 요약_AI_실패시_fallback_summary를_반환한다() throws Exception {
        CalendarEvent event = event("event-summary-fallback", "강남 회식", "강남 목구멍");
        ResolvedLocation resolvedLocation = resolvedLocation(event.rawLocation());

        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event));
        given(googlePlacesLocationResolver.resolve(event.rawLocation())).willReturn(resolvedLocation);
        stubNormalWeather(resolvedLocation, event);
        given(summaryGenerator.generate(any())).willThrow(new RuntimeException("summary failed"));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].summary").value("우산을 챙기고 두꺼운 겉옷 차림으로 준비하면 됩니다."));
    }

    @Test
    void Google_재연동_필요는_401로_매핑된다() throws Exception {
        googleIntegrationRepository.save(expiredReauthRequiredIntegration());

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_401"));
    }

    @Test
    void Google_외부_장애는_503으로_매핑된다() throws Exception {
        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willThrow(new GoogleIntegrationUnavailableException("Google integration is temporarily unavailable"));

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_503"));
    }

    @Test
    void 없는_eventId는_404로_매핑된다() throws Exception {
        given(googleCalendarClient.fetchUpcomingEvents(any(GoogleIntegration.class), any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(event("event-1", "강남 회식", "강남 목구멍")));

        mockMvc.perform(get("/api/recommendations/events/missing-event"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_404"));
    }

    private void stubNormalWeather(ResolvedLocation resolvedLocation, CalendarEvent event) {
        WeatherSnapshot currentWeather = weatherSnapshot(
                currentLocation().displayLocation(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                20,
                20,
                10,
                WeatherType.CLEAR
        );
        WeatherSnapshot startWeather = weatherSnapshot(
                resolvedLocation.displayLocation(),
                event.startAt(),
                19,
                18,
                30,
                WeatherType.CLOUDY
        );
        WeatherSnapshot endWeather = weatherSnapshot(
                resolvedLocation.displayLocation(),
                event.endAt(),
                16,
                14,
                70,
                WeatherType.RAIN
        );

        given(httpWeatherClient.fetchCurrentWeatherResult(any(ResolvedLocation.class)))
                .willReturn(new WeatherClient.CurrentWeatherResult(
                        currentWeather,
                        WeatherClient.WeatherFetchSource.NORMAL
                ));
        given(httpWeatherClient.fetchWeatherRangeResult(any(ResolvedLocation.class), eq(event.startAt()), eq(event.endAt())))
                .willReturn(new WeatherClient.WeatherRangeResult(
                        new WeatherForecastSnapshots(startWeather, endWeather),
                        WeatherClient.WeatherFetchSource.NORMAL
                ));
    }

    private GoogleIntegration activeIntegration() {
        return new GoogleIntegration(
                USER_ID,
                "user@wit.local",
                "active-access-token",
                "refresh-token",
                LocalDateTime.of(2099, 1, 1, 0, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0)
        );
    }

    private GoogleIntegration expiredReauthRequiredIntegration() {
        return new GoogleIntegration(
                USER_ID,
                "user@wit.local",
                "expired-access-token",
                "",
                LocalDateTime.of(2000, 1, 1, 0, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0)
        );
    }

    private CalendarEvent event(String eventId, String title, String rawLocation) {
        return new CalendarEvent(
                eventId,
                title,
                LocalDateTime.of(2026, 4, 7, 18, 0),
                LocalDateTime.of(2026, 4, 7, 21, 0),
                rawLocation
        );
    }

    private ResolvedLocation currentLocation() {
        return ResolvedLocation.resolved(
                "현재 위치",
                "current",
                "서울특별시 강남구",
                37.5172,
                127.0473,
                1.0,
                LocationResolvedBy.RULE
        );
    }

    private ResolvedLocation resolvedLocation(String rawLocation) {
        return ResolvedLocation.resolved(
                rawLocation,
                "목구멍 강남점",
                "서울특별시 강남구 테헤란로 1",
                37.5001,
                127.0362,
                0.85,
                LocationResolvedBy.GOOGLE_PLACES
        );
    }

    private WeatherSnapshot weatherSnapshot(
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
