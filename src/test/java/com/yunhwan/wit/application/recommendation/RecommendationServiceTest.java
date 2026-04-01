package com.yunhwan.wit.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {

    private final LocalDateTime currentTime = LocalDateTime.of(2026, 4, 2, 9, 0);
    private final CalendarEvent calendarEvent = new CalendarEvent(
            "event-1",
            "강남 회식",
            LocalDateTime.of(2026, 4, 2, 18, 0),
            LocalDateTime.of(2026, 4, 2, 21, 0),
            "강남 회식"
    );

    @Test
    void 일정에서_위치와_날씨를_조합해_추천결과를_생성한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        weatherClient.setCurrentWeather(currentLocation, snapshot(currentLocation, currentTime, 24, 24, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.startAt(),
                snapshot(eventLocation, calendarEvent.startAt(), 21, 21, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.endAt(),
                snapshot(eventLocation, calendarEvent.endAt(), 18, 18, 70, WeatherType.RAIN));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                weatherClient,
                new OutfitRuleEngine()
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.outfitDecision()).isNotNull();
        assertThat(result.calendarEvent()).isEqualTo(calendarEvent);
        assertThat(result.resolvedLocation()).isEqualTo(eventLocation);
        assertThat(result.currentWeather().targetTime()).isEqualTo(currentTime);
        assertThat(result.startWeather().targetTime()).isEqualTo(calendarEvent.startAt());
        assertThat(result.endWeather().targetTime()).isEqualTo(calendarEvent.endAt());
        assertThat(result.outfitDecision().needUmbrella()).isTrue();
    }

    @Test
    void 위치해석이_실패하면_현재위치로_대체한다() {
        ResolvedLocation currentLocation = currentLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        weatherClient.setCurrentWeather(currentLocation, snapshot(currentLocation, currentTime, 20, 20, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(currentLocation, calendarEvent.startAt(),
                snapshot(currentLocation, calendarEvent.startAt(), 19, 19, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(currentLocation, calendarEvent.endAt(),
                snapshot(currentLocation, calendarEvent.endAt(), 16, 16, 20, WeatherType.CLOUDY));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> ResolvedLocation.failed(rawLocation),
                () -> currentLocation,
                weatherClient,
                new OutfitRuleEngine()
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.resolvedLocation()).isEqualTo(currentLocation);
        assertThat(result.startWeather().regionName()).isEqualTo(currentLocation.displayLocation());
        assertThat(result.endWeather().regionName()).isEqualTo(currentLocation.displayLocation());
    }

    @Test
    void 날씨조회가_실패해도_안전한_fallback_결과를_반환한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        TrackingRuleEngine ruleEngine = new TrackingRuleEngine();

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                new FailingWeatherClient(),
                ruleEngine
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);
        OutfitDecision decision = result.outfitDecision();

        assertThat(result.weatherFallbackApplied()).isTrue();
        assertThat(result.currentWeather()).isNull();
        assertThat(result.startWeather()).isNull();
        assertThat(result.endWeather()).isNull();
        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
        assertThat(decision.needUmbrella()).isTrue();
        assertThat(ruleEngine.called).isFalse();
    }

    @Test
    void 위치해석기_예외가_발생하면_현재위치로_대체한다() {
        ResolvedLocation currentLocation = currentLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        weatherClient.setCurrentWeather(currentLocation, snapshot(currentLocation, currentTime, 20, 20, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(currentLocation, calendarEvent.startAt(),
                snapshot(currentLocation, calendarEvent.startAt(), 19, 19, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(currentLocation, calendarEvent.endAt(),
                snapshot(currentLocation, calendarEvent.endAt(), 16, 16, 20, WeatherType.CLOUDY));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> {
                    throw new RuntimeException("resolver failure");
                },
                () -> currentLocation,
                weatherClient,
                new OutfitRuleEngine()
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.resolvedLocation()).isEqualTo(currentLocation);
        assertThat(result.weatherFallbackApplied()).isFalse();
    }

    @Test
    void 날씨클라이언트가_null을_반환하면_명시적_fallback_결과를_반환한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        TrackingRuleEngine ruleEngine = new TrackingRuleEngine();

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                new NullWeatherClient(),
                ruleEngine
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isTrue();
        assertThat(result.currentWeather()).isNull();
        assertThat(result.startWeather()).isNull();
        assertThat(result.endWeather()).isNull();
        assertThat(ruleEngine.called).isFalse();
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

    private ResolvedLocation eventLocation() {
        return ResolvedLocation.resolved(
                "강남 회식",
                "강남",
                "서울특별시 강남구",
                37.5172,
                127.0473,
                1.0,
                LocationResolvedBy.RULE
        );
    }

    private WeatherSnapshot snapshot(
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

    private static final class StubWeatherClient implements WeatherClient {

        private final Map<String, WeatherSnapshot> currentSnapshots = new HashMap<>();
        private final Map<String, WeatherSnapshot> timedSnapshots = new HashMap<>();

        private void setCurrentWeather(ResolvedLocation location, WeatherSnapshot snapshot) {
            currentSnapshots.put(key(location.displayLocation(), null), snapshot);
        }

        private void setTimedWeather(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot snapshot) {
            timedSnapshots.put(key(location.displayLocation(), targetTime), snapshot);
        }

        @Override
        public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
            return currentSnapshots.get(key(location.displayLocation(), null));
        }

        @Override
        public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
            return timedSnapshots.get(key(location.displayLocation(), targetTime));
        }

        private String key(String location, LocalDateTime targetTime) {
            return location + "::" + targetTime;
        }
    }

    private static final class FailingWeatherClient implements WeatherClient {

        @Override
        public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
            throw new RuntimeException("weather failure");
        }

        @Override
        public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
            throw new RuntimeException("weather failure");
        }
    }

    private static final class NullWeatherClient implements WeatherClient {

        @Override
        public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
            return null;
        }

        @Override
        public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
            return null;
        }
    }

    private static final class TrackingRuleEngine extends OutfitRuleEngine {

        private boolean called;

        @Override
        public OutfitDecision decide(
                WeatherSnapshot currentWeather,
                WeatherSnapshot startWeather,
                WeatherSnapshot endWeather
        ) {
            called = true;
            return super.decide(currentWeather, startWeather, endWeather);
        }
    }
}
