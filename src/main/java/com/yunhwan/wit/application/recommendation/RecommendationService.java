package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.summary.SummaryGenerationInput;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.application.weather.WeatherForecastSnapshots;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import com.yunhwan.wit.domain.rule.WeatherFailureFallbackDecisionProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final LocationResolver locationResolver;
    private final CurrentLocationProvider currentLocationProvider;
    private final WeatherClient weatherClient;
    private final OutfitRuleEngine outfitRuleEngine;
    private final WeatherFailureFallbackDecisionProvider weatherFailureFallbackDecisionProvider;
    private final SummaryGenerator summaryGenerator;
    private final RecommendationCache recommendationCache;
    private final Clock clock;

    public RecommendationService(
            LocationResolver locationResolver,
            CurrentLocationProvider currentLocationProvider,
            WeatherClient weatherClient,
            OutfitRuleEngine outfitRuleEngine,
            WeatherFailureFallbackDecisionProvider weatherFailureFallbackDecisionProvider,
            SummaryGenerator summaryGenerator,
            RecommendationCache recommendationCache,
            Clock clock
    ) {
        this.locationResolver = Objects.requireNonNull(locationResolver, "locationResolver must not be null");
        this.currentLocationProvider = Objects.requireNonNull(
                currentLocationProvider,
                "currentLocationProvider must not be null"
        );
        this.weatherClient = Objects.requireNonNull(weatherClient, "weatherClient must not be null");
        this.outfitRuleEngine = Objects.requireNonNull(outfitRuleEngine, "outfitRuleEngine must not be null");
        this.weatherFailureFallbackDecisionProvider = Objects.requireNonNull(
                weatherFailureFallbackDecisionProvider,
                "weatherFailureFallbackDecisionProvider must not be null"
        );
        this.summaryGenerator = Objects.requireNonNull(summaryGenerator, "summaryGenerator must not be null");
        this.recommendationCache = Objects.requireNonNull(recommendationCache, "recommendationCache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RecommendationResult recommend(CalendarEvent calendarEvent) {
        Objects.requireNonNull(calendarEvent, "calendarEvent must not be null");
        log.info("[RecommendationDebug] recommend start. eventId={}", calendarEvent.eventId());

        LocalDateTime cacheTime = currentCacheTime();
        RecommendationResult cached = readFromCache(calendarEvent, cacheTime);
        if (cached != null) {
            log.info("[RecommendationDebug] recommend cache hit. eventId={}", calendarEvent.eventId());
            return cached;
        }

        ResolvedLocation currentLocation = currentLocationProvider.getCurrentLocation();
        LocationResolutionOutcome locationResolutionOutcome = resolveEventLocation(calendarEvent, currentLocation);
        ResolvedLocation resolvedLocation = locationResolutionOutcome.location();
        ResolvedLocation currentWeatherLocation = selectCurrentWeatherLocation(currentLocation, resolvedLocation);

        WeatherClient.CurrentWeatherResult currentWeatherResult = fetchCurrentWeather(currentWeatherLocation);
        WeatherClient.WeatherRangeResult weatherRangeResult = fetchWeatherRange(
                resolvedLocation,
                calendarEvent.startAt(),
                calendarEvent.endAt()
        );
        WeatherSnapshot currentWeather = currentWeatherResult == null ? null : currentWeatherResult.snapshot();
        WeatherForecastSnapshots forecastSnapshots = weatherRangeResult == null ? null : weatherRangeResult.snapshots();
        WeatherSnapshot startWeather = forecastSnapshots == null ? null : forecastSnapshots.startWeather();
        WeatherSnapshot endWeather = forecastSnapshots == null ? null : forecastSnapshots.endWeather();

        if (currentWeather == null || startWeather == null || endWeather == null) {
            log.info(
                    "[RecommendationDebug] weather fallback used. source=SAFE_DEFAULT, eventId={}",
                    calendarEvent.eventId()
            );
            OutfitDecision fallbackDecision = summarize(
                    weatherFailureFallbackDecisionProvider.provide(),
                    null,
                    null,
                    null
            );
            RecommendationResult fallbackResult = new RecommendationResult(
                    fallbackDecision,
                    calendarEvent,
                    resolvedLocation,
                    null,
                    null,
                    null,
                    true,
                    locationResolutionOutcome.fallbackApplied(),
                    RecommendationWeatherSource.SAFE_DEFAULT
            );
            writeToCache(calendarEvent, cacheTime, fallbackResult);
            log.info("[RecommendationDebug] recommend end with weather fallback. eventId={}", calendarEvent.eventId());
            return fallbackResult;
        }

        log.info("[RecommendationDebug] rule engine before. eventId={}", calendarEvent.eventId());
        OutfitDecision outfitDecision = summarize(
                outfitRuleEngine.decide(currentWeather, startWeather, endWeather),
                currentWeather,
                startWeather,
                endWeather
        );
        log.info(
                "[RecommendationDebug] rule engine after. eventId={}, needUmbrella={}, recommendedOutfitText={}",
                calendarEvent.eventId(),
                outfitDecision.needUmbrella(),
                outfitDecision.recommendedOutfitText()
        );

        RecommendationResult recommendationResult = new RecommendationResult(
                outfitDecision,
                calendarEvent,
                resolvedLocation,
                currentWeather,
                startWeather,
                endWeather,
                false,
                locationResolutionOutcome.fallbackApplied(),
                resolveWeatherSource(currentWeatherResult, weatherRangeResult)
        );
        writeToCache(calendarEvent, cacheTime, recommendationResult);
        log.info("[RecommendationDebug] recommend end. eventId={}", calendarEvent.eventId());
        return recommendationResult;
    }

    private RecommendationResult readFromCache(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
        try {
            log.info(
                    "[RecommendationDebug] recommendation cache read before. eventId={}, cacheTime={}",
                    calendarEvent.eventId(),
                    cacheTime
            );
            RecommendationResult result = recommendationCache.find(calendarEvent, cacheTime).orElse(null);
            log.info(
                    "[RecommendationDebug] recommendation cache read after. eventId={}, hit={}",
                    calendarEvent.eventId(),
                    result != null
            );
            return result;
        } catch (RuntimeException exception) {
            log.warn("Recommendation cache read failed. eventId={}", calendarEvent.eventId(), exception);
            return null;
        }
    }

    private void writeToCache(
            CalendarEvent calendarEvent,
            LocalDateTime cacheTime,
            RecommendationResult recommendationResult
    ) {
        try {
            log.info(
                    "[RecommendationDebug] recommendation cache write before. eventId={}, cacheTime={}",
                    calendarEvent.eventId(),
                    cacheTime
            );
            recommendationCache.put(calendarEvent, cacheTime, recommendationResult);
            log.info("[RecommendationDebug] recommendation cache write after. eventId={}", calendarEvent.eventId());
        } catch (RuntimeException exception) {
            log.warn("Recommendation cache write failed. eventId={}", calendarEvent.eventId(), exception);
        }
    }

    private LocalDateTime currentCacheTime() {
        LocalDateTime now = LocalDateTime.now(clock);
        int minuteBucket = now.getMinute() < 30 ? 0 : 30;
        return now.withMinute(minuteBucket).withSecond(0).withNano(0);
    }

    private LocationResolutionOutcome resolveEventLocation(CalendarEvent calendarEvent, ResolvedLocation currentLocation) {
        try {
            log.info(
                    "[RecommendationDebug] location resolve before. eventId={}, rawLocation={}",
                    calendarEvent.eventId(),
                    calendarEvent.rawLocation()
            );
            ResolvedLocation resolvedLocation = locationResolver.resolve(calendarEvent.rawLocation());
            if (resolvedLocation.status() == LocationResolutionStatus.FAILED) {
                log.info(
                        "[RecommendationDebug] location resolve after. eventId={}, status=FAILED, fallback=current",
                        calendarEvent.eventId()
                );
                return new LocationResolutionOutcome(currentLocation, true);
            }
            log.info(
                    "[RecommendationDebug] location resolve after. eventId={}, status={}, resolvedBy={}, displayLocation={}",
                    calendarEvent.eventId(),
                    resolvedLocation.status(),
                    resolvedLocation.resolvedBy(),
                    resolvedLocation.displayLocation()
            );
            return new LocationResolutionOutcome(resolvedLocation, false);
        } catch (RuntimeException exception) {
            log.warn(
                    "[RecommendationDebug] location resolve failed. eventId={}, fallback=current",
                    calendarEvent.eventId(),
                    exception
            );
            return new LocationResolutionOutcome(currentLocation, true);
        }
    }

    private ResolvedLocation selectCurrentWeatherLocation(
            ResolvedLocation currentLocation,
            ResolvedLocation resolvedLocation
    ) {
        boolean hasRealCurrentLocation = currentLocationProvider.hasRealCurrentLocation();
        ResolvedLocation currentWeatherLocation = hasRealCurrentLocation ? currentLocation : resolvedLocation;
        log.info(
                "[RecommendationDebug] current weather location selected. currentLocationSource={}, currentWeatherSource={}, currentLocation={}, resolvedLocation={}, currentWeatherLocation={}",
                hasRealCurrentLocation ? "REAL" : "DEFAULT",
                hasRealCurrentLocation ? "CURRENT_LOCATION" : "FALLBACK_TO_DESTINATION",
                currentLocation.displayLocation(),
                resolvedLocation.displayLocation(),
                currentWeatherLocation.displayLocation()
        );
        return currentWeatherLocation;
    }

    private WeatherClient.CurrentWeatherResult fetchCurrentWeather(ResolvedLocation location) {
        try {
            log.info(
                    "[RecommendationDebug] current weather before. location={}, lat={}, lng={}",
                    location.displayLocation(),
                    location.lat(),
                    location.lng()
            );
            WeatherClient.CurrentWeatherResult weatherResult = weatherClient.fetchCurrentWeatherResult(location);
            WeatherSnapshot snapshot = weatherResult.snapshot();
            log.info(
                    "[RecommendationDebug] current weather after. location={}, targetTime={}",
                    location.displayLocation(),
                    snapshot.targetTime()
            );
            return weatherResult;
        } catch (RuntimeException exception) {
            log.warn("[RecommendationDebug] current weather failed. location={}", location.displayLocation(), exception);
            return null;
        }
    }

    private WeatherClient.WeatherRangeResult fetchWeatherRange(
            ResolvedLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        try {
            log.info(
                    "[RecommendationDebug] forecast weather range before. location={}, lat={}, lng={}, startTime={}, endTime={}",
                    location.displayLocation(),
                    location.lat(),
                    location.lng(),
                    startTime,
                    endTime
            );
            WeatherClient.WeatherRangeResult weatherRangeResult = weatherClient.fetchWeatherRangeResult(
                    location,
                    startTime,
                    endTime
            );
            WeatherForecastSnapshots snapshots = weatherRangeResult.snapshots();
            log.info(
                    "[RecommendationDebug] forecast weather range after. location={}, startTime={}, endTime={}, startType={}, endType={}",
                    location.displayLocation(),
                    startTime,
                    endTime,
                    snapshots.startWeather().weatherType(),
                    snapshots.endWeather().weatherType()
            );
            return weatherRangeResult;
        } catch (RuntimeException exception) {
            log.warn(
                    "[RecommendationDebug] forecast weather range failed. location={}, startTime={}, endTime={}",
                    location.displayLocation(),
                    startTime,
                    endTime,
                    exception
            );
            return null;
        }
    }

    private OutfitDecision summarize(
            OutfitDecision outfitDecision,
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        try {
            log.info(
                    "[RecommendationDebug] summary generation before. needUmbrella={}, recommendedOutfitText={}",
                    outfitDecision.needUmbrella(),
                    outfitDecision.recommendedOutfitText()
            );
            String summary = summaryGenerator.generate(new SummaryGenerationInput(
                    outfitDecision,
                    currentWeather,
                    startWeather,
                    endWeather
            ));
            log.info(
                    "[RecommendationDebug] summary generation after. success=true, needUmbrella={}, recommendedOutfitText={}",
                    outfitDecision.needUmbrella(),
                    outfitDecision.recommendedOutfitText()
            );
            return outfitDecision.withAiSummary(summary);
        } catch (RuntimeException exception) {
            log.warn(
                    "[RecommendationDebug] summary generation failed. fallback summary will be used. needUmbrella={}, recommendedOutfitLevel={}",
                    outfitDecision.needUmbrella(),
                    outfitDecision.recommendedOutfitLevel(),
                    exception
            );
            return outfitDecision.withAiSummary(buildFallbackSummary(outfitDecision));
        }
    }

    private String buildFallbackSummary(OutfitDecision outfitDecision) {
        String umbrellaText = outfitDecision.needUmbrella() ? "우산을 챙기고" : "우산은 없어도 되고";
        return umbrellaText + " " + outfitDecision.recommendedOutfitText() + " 차림으로 준비하면 됩니다.";
    }

    private RecommendationWeatherSource resolveWeatherSource(
            WeatherClient.CurrentWeatherResult currentWeatherResult,
            WeatherClient.WeatherRangeResult weatherRangeResult
    ) {
        if (currentWeatherResult.source() == WeatherClient.WeatherFetchSource.CACHE
                || weatherRangeResult.source() == WeatherClient.WeatherFetchSource.CACHE) {
            return RecommendationWeatherSource.CACHE;
        }
        return RecommendationWeatherSource.NORMAL;
    }

    private record LocationResolutionOutcome(
            ResolvedLocation location,
            boolean fallbackApplied
    ) {
    }
}
