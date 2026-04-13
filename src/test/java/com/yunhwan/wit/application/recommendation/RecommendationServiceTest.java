package com.yunhwan.wit.application.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.summary.SummaryGenerationInput;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.CachingWeatherClient;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.application.weather.WeatherCache;
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

class RecommendationServiceTest {

    private final LocalDateTime currentTime = LocalDateTime.of(2026, 4, 2, 9, 0);
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneId.of("Asia/Seoul"));
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
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
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
        assertThat(result.outfitDecision().aiSummary()).isEqualTo("우산을 챙기고 긴팔 + 가벼운 겉옷 차림을 추천합니다.");
    }

    @Test
    void 요약생성은_규칙엔진_결과와_날씨정보를_함께_입력으로_받는다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        TrackingSummaryGenerator summaryGenerator = new TrackingSummaryGenerator();
        weatherClient.setCurrentWeather(currentLocation, snapshot(currentLocation, currentTime, 24, 24, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.startAt(),
                snapshot(eventLocation, calendarEvent.startAt(), 21, 21, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.endAt(),
                snapshot(eventLocation, calendarEvent.endAt(), 18, 18, 70, WeatherType.RAIN));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                weatherClient,
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                summaryGenerator,
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(summaryGenerator.capturedInput).isNotNull();
        assertThat(summaryGenerator.capturedInput.outfitDecision().needUmbrella()).isTrue();
        assertThat(summaryGenerator.capturedInput.currentWeather()).isEqualTo(result.currentWeather());
        assertThat(summaryGenerator.capturedInput.startWeather()).isEqualTo(result.startWeather());
        assertThat(summaryGenerator.capturedInput.endWeather()).isEqualTo(result.endWeather());
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
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.resolvedLocation()).isEqualTo(currentLocation);
        assertThat(result.startWeather().regionName()).isEqualTo(currentLocation.displayLocation());
        assertThat(result.endWeather().regionName()).isEqualTo(currentLocation.displayLocation());
    }

    @Test
    void 실제_현재위치가_없으면_currentWeather에_목적지를_사용한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        weatherClient.setCurrentWeather(eventLocation, snapshot(eventLocation, currentTime, 22, 22, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.startAt(),
                snapshot(eventLocation, calendarEvent.startAt(), 21, 21, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.endAt(),
                snapshot(eventLocation, calendarEvent.endAt(), 18, 18, 70, WeatherType.RAIN));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                defaultCurrentLocationProvider(currentLocation),
                weatherClient,
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.currentWeather().regionName()).isEqualTo(eventLocation.displayLocation());
    }

    @Test
    void 날씨조회가_실패해도_안전한_fallback_결과를_반환한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        TrackingRuleEngine ruleEngine = new TrackingRuleEngine();
        TrackingFallbackDecisionProvider fallbackDecisionProvider = new TrackingFallbackDecisionProvider();

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                new FailingWeatherClient(),
                ruleEngine,
                fallbackDecisionProvider,
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);
        OutfitDecision decision = result.outfitDecision();

        assertThat(result.weatherFallbackApplied()).isTrue();
        assertThat(result.currentWeather()).isNull();
        assertThat(result.startWeather()).isNull();
        assertThat(result.endWeather()).isNull();
        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
        assertThat(decision.needUmbrella()).isTrue();
        assertThat(decision.aiSummary()).isEqualTo("우산을 챙기고 두꺼운 겉옷 차림을 추천합니다.");
        assertThat(ruleEngine.called).isFalse();
        assertThat(fallbackDecisionProvider.called).isTrue();
    }

    @Test
    void 날씨_API가_실패해도_latest_cache가_있으면_캐시된_날씨로_추천을_계속_생성한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        InMemoryWeatherCache weatherCache = new InMemoryWeatherCache();
        weatherCache.putCurrent(
                currentLocation,
                LocalDateTime.of(2026, 4, 2, 8, 0),
                snapshot(currentLocation, LocalDateTime.of(2026, 4, 2, 8, 0), 20, 20, 10, WeatherType.CLEAR)
        );
        weatherCache.putForecast(
                eventLocation,
                LocalDateTime.of(2026, 4, 2, 15, 0),
                snapshot(eventLocation, LocalDateTime.of(2026, 4, 2, 15, 0), 18, 18, 60, WeatherType.RAIN)
        );
        WeatherClient weatherClient = new CachingWeatherClient(new FailingWeatherClient(), weatherCache, clock);
        TrackingRuleEngine ruleEngine = new TrackingRuleEngine();
        TrackingFallbackDecisionProvider fallbackDecisionProvider = new TrackingFallbackDecisionProvider();

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                weatherClient,
                ruleEngine,
                fallbackDecisionProvider,
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isFalse();
        assertThat(result.currentWeather()).isNotNull();
        assertThat(result.startWeather()).isNotNull();
        assertThat(result.endWeather()).isNotNull();
        assertThat(ruleEngine.called).isTrue();
        assertThat(fallbackDecisionProvider.called).isFalse();
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
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
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
        TrackingFallbackDecisionProvider fallbackDecisionProvider = new TrackingFallbackDecisionProvider();

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                new NullWeatherClient(),
                ruleEngine,
                fallbackDecisionProvider,
                new StubSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.weatherFallbackApplied()).isTrue();
        assertThat(result.currentWeather()).isNull();
        assertThat(result.startWeather()).isNull();
        assertThat(result.endWeather()).isNull();
        assertThat(ruleEngine.called).isFalse();
        assertThat(fallbackDecisionProvider.called).isTrue();
    }

    @Test
    void 요약생성이_실패해도_fallback_summary를_넣어_결과를_반환한다() {
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
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new FailingSummaryGenerator(),
                new InMemoryRecommendationCache(),
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result.outfitDecision().aiSummary()).isEqualTo("우산을 챙기고 긴팔 + 가벼운 겉옷 차림으로 준비하면 됩니다.");
    }

    @Test
    void 추천캐시_히트면_전체흐름을_다시_실행하지_않는다() {
        RecommendationResult cachedResult = new RecommendationResult(
                new OutfitDecision(
                        true,
                        RecommendedOutfitLevel.LONG_SLEEVE,
                        "긴팔",
                        "cached umbrella reason",
                        "cached outfit reason",
                        -3,
                        "cached weather change",
                        "cached summary"
                ),
                calendarEvent,
                eventLocation(),
                snapshot(currentLocation(), currentTime, 20, 20, 10, WeatherType.CLEAR),
                snapshot(eventLocation(), calendarEvent.startAt(), 19, 19, 20, WeatherType.CLOUDY),
                snapshot(eventLocation(), calendarEvent.endAt(), 18, 18, 40, WeatherType.CLOUDY),
                false
        );
        InMemoryRecommendationCache recommendationCache = new InMemoryRecommendationCache();
        recommendationCache.put(calendarEvent, LocalDateTime.of(2026, 4, 2, 9, 0), cachedResult);

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> {
                    throw new AssertionError("resolver should not be called on cache hit");
                },
                this::currentLocation,
                new FailingWeatherClient(),
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new StubSummaryGenerator(),
                recommendationCache,
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(result).isEqualTo(cachedResult);
    }

    @Test
    void 추천캐시_미스면_계산결과를_캐시에_저장한다() {
        ResolvedLocation currentLocation = currentLocation();
        ResolvedLocation eventLocation = eventLocation();
        StubWeatherClient weatherClient = new StubWeatherClient();
        InMemoryRecommendationCache recommendationCache = new InMemoryRecommendationCache();
        weatherClient.setCurrentWeather(currentLocation, snapshot(currentLocation, currentTime, 24, 24, 10, WeatherType.CLEAR));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.startAt(),
                snapshot(eventLocation, calendarEvent.startAt(), 21, 21, 20, WeatherType.CLOUDY));
        weatherClient.setTimedWeather(eventLocation, calendarEvent.endAt(),
                snapshot(eventLocation, calendarEvent.endAt(), 18, 18, 70, WeatherType.RAIN));

        RecommendationService recommendationService = new RecommendationService(
                rawLocation -> eventLocation,
                () -> currentLocation,
                weatherClient,
                new OutfitRuleEngine(),
                new WeatherFailureFallbackDecisionProvider(),
                new TrackingSummaryGenerator(),
                recommendationCache,
                clock
        );

        RecommendationResult result = recommendationService.recommend(calendarEvent);

        assertThat(recommendationCache.find(calendarEvent, LocalDateTime.of(2026, 4, 2, 9, 0))).contains(result);
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

    private CurrentLocationProvider defaultCurrentLocationProvider(ResolvedLocation currentLocation) {
        return new CurrentLocationProvider() {
            @Override
            public ResolvedLocation getCurrentLocation() {
                return currentLocation;
            }

            @Override
            public boolean hasRealCurrentLocation() {
                return false;
            }
        };
    }

    private static final class StubSummaryGenerator implements SummaryGenerator {

        @Override
        public String generate(SummaryGenerationInput input) {
            OutfitDecision outfitDecision = input.outfitDecision();
            String umbrellaText = outfitDecision.needUmbrella() ? "우산을 챙기고" : "우산 없이";
            return umbrellaText + " " + outfitDecision.recommendedOutfitText() + " 차림을 추천합니다.";
        }
    }

    private static final class FailingSummaryGenerator implements SummaryGenerator {

        @Override
        public String generate(SummaryGenerationInput input) {
            throw new RuntimeException("summary failure");
        }
    }

    private static final class TrackingSummaryGenerator implements SummaryGenerator {

        private SummaryGenerationInput capturedInput;

        @Override
        public String generate(SummaryGenerationInput input) {
            this.capturedInput = input;
            OutfitDecision outfitDecision = input.outfitDecision();
            String umbrellaText = outfitDecision.needUmbrella() ? "우산을 챙기고" : "우산 없이";
            return umbrellaText + " " + outfitDecision.recommendedOutfitText() + " 차림을 추천합니다.";
        }
    }

    private static final class InMemoryRecommendationCache implements RecommendationCache {

        private final Map<String, RecommendationResult> store = new HashMap<>();

        @Override
        public Optional<RecommendationResult> find(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
            return Optional.ofNullable(store.get(key(calendarEvent, cacheTime)));
        }

        @Override
        public void put(
                CalendarEvent calendarEvent,
                LocalDateTime cacheTime,
                RecommendationResult recommendationResult
        ) {
            store.put(key(calendarEvent, cacheTime), recommendationResult);
        }

        private String key(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
            return calendarEvent.eventId() + "::" + cacheTime;
        }
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

    private static final class TrackingFallbackDecisionProvider extends WeatherFailureFallbackDecisionProvider {

        private boolean called;

        @Override
        public OutfitDecision provide() {
            called = true;
            return super.provide();
        }
    }

    private static final class InMemoryWeatherCache implements WeatherCache {

        private final Map<String, WeatherSnapshot> store = new HashMap<>();

        @Override
        public Optional<WeatherSnapshot> findCurrent(ResolvedLocation location, LocalDateTime cacheTime) {
            return Optional.ofNullable(store.get(key("current", location, cacheTime)));
        }

        @Override
        public Optional<WeatherSnapshot> findForecast(ResolvedLocation location, LocalDateTime targetTime) {
            return Optional.ofNullable(store.get(key("forecast", location, targetTime)));
        }

        @Override
        public Optional<WeatherSnapshot> findLatestCurrent(ResolvedLocation location) {
            return store.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix("current", location)))
                    .max(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue);
        }

        @Override
        public Optional<WeatherSnapshot> findLatestForecast(ResolvedLocation location) {
            return store.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix("forecast", location)))
                    .max(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue);
        }

        @Override
        public void putCurrent(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot) {
            store.put(key("current", location, cacheTime), weatherSnapshot);
        }

        @Override
        public void putForecast(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot) {
            store.put(key("forecast", location, targetTime), weatherSnapshot);
        }

        private String key(String type, ResolvedLocation location, LocalDateTime targetTime) {
            return prefix(type, location) + targetTime;
        }

        private String prefix(String type, ResolvedLocation location) {
            return type + ":" + location.lat() + ":" + location.lng() + ":";
        }
    }
}
