package com.yunhwan.wit.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.application.location.AiLocationFallbackResolver;
import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.DefaultLocationResolver;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.location.RuleBasedLocationResolver;
import com.yunhwan.wit.application.recommendation.RecommendationCache;
import com.yunhwan.wit.application.summary.SummaryGenerationInput;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import com.yunhwan.wit.domain.rule.WeatherFailureFallbackDecisionProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecommendationServiceEndToEndTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final CurrentLocationProvider currentLocationProvider = () -> ResolvedLocation.resolved(
            "현재 위치",
            "current",
            "서울특별시 강남구",
            37.5172,
            127.0473,
            1.0,
            LocationResolvedBy.RULE
    );

    @Test
    void stub_일정으로_전체_추천흐름이_연결된다() {
        CalendarEvent event = new CalendarEvent(
                "event-1",
                "강남 회식",
                LocalDateTime.of(2026, 4, 2, 18, 0),
                LocalDateTime.of(2026, 4, 2, 21, 0),
                "강남 회식"
        );
        StubWeatherClient weatherClient = new StubWeatherClient();
        ResolvedLocation currentLocation = currentLocationProvider.getCurrentLocation();
        ResolvedLocation eventLocation = ResolvedLocation.resolved(
                "강남 회식",
                "강남",
                "서울특별시 강남구",
                37.5172,
                127.0473,
                1.0,
                LocationResolvedBy.RULE
        );

        weatherClient.setCurrent(currentLocation, snapshot(currentLocation, LocalDateTime.of(2026, 4, 2, 9, 0), 25, 25, 10, WeatherType.CLEAR));
        weatherClient.setTimed(eventLocation, event.startAt(), snapshot(eventLocation, event.startAt(), 21, 21, 30, WeatherType.CLOUDY));
        weatherClient.setTimed(eventLocation, event.endAt(), snapshot(eventLocation, event.endAt(), 17, 17, 70, WeatherType.RAIN));

        RecommendationService service = new RecommendationService(
                locationResolver(rawLocation -> ResolvedLocation.failed(rawLocation)),
                currentLocationProvider,
                weatherClient,
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = service.recommend(event);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.resolvedLocation().displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.startWeather()).isNotNull();
        assertThat(result.endWeather()).isNotNull();
        assertThat(result.outfitDecision().needUmbrella()).isTrue();
        assertThat(result.outfitDecision().recommendedOutfitLevel())
                .isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER);
        assertThat(result.outfitDecision().weatherChangeSummary()).contains("더 쌀쌀");
        assertThat(result.outfitDecision().aiSummary()).isEqualTo("우산을 챙기고 긴팔 + 가벼운 겉옷 차림을 추천합니다.");
    }

    @Test
    void 위치해석이_실패하면_현재위치_기준으로_전체흐름이_계속된다() {
        CalendarEvent event = new CalendarEvent(
                "event-2",
                "정체불명 일정",
                LocalDateTime.of(2026, 4, 3, 10, 0),
                LocalDateTime.of(2026, 4, 3, 12, 0),
                "어딘가 모름"
        );
        StubWeatherClient weatherClient = new StubWeatherClient();
        ResolvedLocation currentLocation = currentLocationProvider.getCurrentLocation();

        weatherClient.setCurrent(currentLocation, snapshot(currentLocation, LocalDateTime.of(2026, 4, 3, 8, 0), 19, 19, 10, WeatherType.CLEAR));
        weatherClient.setTimed(currentLocation, event.startAt(), snapshot(currentLocation, event.startAt(), 18, 18, 10, WeatherType.CLOUDY));
        weatherClient.setTimed(currentLocation, event.endAt(), snapshot(currentLocation, event.endAt(), 15, 15, 10, WeatherType.CLOUDY));

        RecommendationService service = new RecommendationService(
                locationResolver(rawLocation -> ResolvedLocation.failed(rawLocation)),
                currentLocationProvider,
                weatherClient,
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = service.recommend(event);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.resolvedLocation()).isEqualTo(currentLocation);
        assertThat(result.startWeather().regionName()).isEqualTo(currentLocation.displayLocation());
        assertThat(result.endWeather().regionName()).isEqualTo(currentLocation.displayLocation());
    }

    @Test
    void 날씨조회가_실패하면_domain_fallback_추천을_반환한다() {
        CalendarEvent event = new CalendarEvent(
                "event-3",
                "판교 미팅",
                LocalDateTime.of(2026, 4, 4, 14, 0),
                LocalDateTime.of(2026, 4, 4, 16, 0),
                "판교"
        );
        TrackingFallbackDecisionProvider fallbackDecisionProvider = new TrackingFallbackDecisionProvider();

        RecommendationService service = new RecommendationService(
                locationResolver(rawLocation -> ResolvedLocation.failed(rawLocation)),
                currentLocationProvider,
                new FailingWeatherClient(),
                new OutfitRuleEngine(),
                fallbackDecisionProvider,
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = service.recommend(event);

        assertThat(result.weatherFallbackApplied()).isTrue();
        assertThat(result.currentWeather()).isNull();
        assertThat(result.startWeather()).isNull();
        assertThat(result.endWeather()).isNull();
        assertThat(result.outfitDecision().recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
        assertThat(result.outfitDecision().needUmbrella()).isTrue();
        assertThat(result.outfitDecision().aiSummary()).isEqualTo("우산을 챙기고 두꺼운 겉옷 차림을 추천합니다.");
        assertThat(fallbackDecisionProvider.called).isTrue();
    }

    private LocationResolver locationResolver(AiLocationFallbackResolver aiLocationFallbackResolver) {
        return new DefaultLocationResolver(
                new RuleBasedLocationResolver(),
                ResolvedLocation::failed,
                aiLocationFallbackResolver
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

        private void setCurrent(ResolvedLocation location, WeatherSnapshot snapshot) {
            currentSnapshots.put(location.displayLocation(), snapshot);
        }

        private void setTimed(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot snapshot) {
            timedSnapshots.put(location.displayLocation() + "::" + targetTime, snapshot);
        }

        @Override
        public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
            return currentSnapshots.get(location.displayLocation());
        }

        @Override
        public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
            return timedSnapshots.get(location.displayLocation() + "::" + targetTime);
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

    private static final class TrackingFallbackDecisionProvider extends WeatherFailureFallbackDecisionProvider {

        private boolean called;

        @Override
        public OutfitDecision provide() {
            called = true;
            return super.provide();
        }
    }

    private static final class StubSummaryGenerator implements SummaryGenerator {

        @Override
        public String generate(SummaryGenerationInput input) {
            OutfitDecision outfitDecision = input.outfitDecision();
            String umbrellaText = outfitDecision.needUmbrella() ? "우산을 챙기고" : "우산 없이";
            return umbrellaText + " " + outfitDecision.recommendedOutfitText() + " 차림을 추천합니다.";
        }
    }

    private static final class InMemoryRecommendationCache implements RecommendationCache {

        private final Map<String, RecommendationResult> store = new HashMap<>();

        @Override
        public Optional<RecommendationResult> find(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
            return Optional.ofNullable(store.get(calendarEvent.eventId() + "::" + cacheTime));
        }

        @Override
        public void put(
                CalendarEvent calendarEvent,
                LocalDateTime cacheTime,
                RecommendationResult recommendationResult
        ) {
            store.put(calendarEvent.eventId() + "::" + cacheTime, recommendationResult);
        }
    }
}
