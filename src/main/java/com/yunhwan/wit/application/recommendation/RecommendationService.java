package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherClient;
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

        LocalDateTime cacheTime = currentCacheTime();
        RecommendationResult cached = readFromCache(calendarEvent, cacheTime);
        if (cached != null) {
            return cached;
        }

        ResolvedLocation currentLocation = currentLocationProvider.getCurrentLocation();
        ResolvedLocation resolvedLocation = resolveEventLocation(calendarEvent, currentLocation);

        WeatherSnapshot currentWeather = fetchCurrentWeather(currentLocation);
        WeatherSnapshot startWeather = fetchWeatherAt(resolvedLocation, calendarEvent.startAt());
        WeatherSnapshot endWeather = fetchWeatherAt(resolvedLocation, calendarEvent.endAt());

        if (currentWeather == null || startWeather == null || endWeather == null) {
            RecommendationResult fallbackResult = new RecommendationResult(
                    summarize(weatherFailureFallbackDecisionProvider.provide()),
                    calendarEvent,
                    resolvedLocation,
                    null,
                    null,
                    null,
                    true
            );
            writeToCache(calendarEvent, cacheTime, fallbackResult);
            return fallbackResult;
        }

        OutfitDecision outfitDecision = summarize(outfitRuleEngine.decide(currentWeather, startWeather, endWeather));

        RecommendationResult recommendationResult = new RecommendationResult(
                outfitDecision,
                calendarEvent,
                resolvedLocation,
                currentWeather,
                startWeather,
                endWeather,
                false
        );
        writeToCache(calendarEvent, cacheTime, recommendationResult);
        return recommendationResult;
    }

    private RecommendationResult readFromCache(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
        try {
            return recommendationCache.find(calendarEvent, cacheTime).orElse(null);
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
            recommendationCache.put(calendarEvent, cacheTime, recommendationResult);
        } catch (RuntimeException exception) {
            log.warn("Recommendation cache write failed. eventId={}", calendarEvent.eventId(), exception);
        }
    }

    private LocalDateTime currentCacheTime() {
        LocalDateTime now = LocalDateTime.now(clock);
        int minuteBucket = now.getMinute() < 30 ? 0 : 30;
        return now.withMinute(minuteBucket).withSecond(0).withNano(0);
    }

    private ResolvedLocation resolveEventLocation(CalendarEvent calendarEvent, ResolvedLocation currentLocation) {
        try {
            ResolvedLocation resolvedLocation = locationResolver.resolve(calendarEvent.rawLocation());
            if (resolvedLocation.status() == LocationResolutionStatus.FAILED) {
                return currentLocation;
            }
            return resolvedLocation;
        } catch (RuntimeException exception) {
            return currentLocation;
        }
    }

    private WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
        try {
            return weatherClient.fetchCurrentWeather(location);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private WeatherSnapshot fetchWeatherAt(ResolvedLocation location, java.time.LocalDateTime targetTime) {
        try {
            return weatherClient.fetchWeatherAt(location, targetTime);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private OutfitDecision summarize(OutfitDecision outfitDecision) {
        try {
            return outfitDecision.withAiSummary(summaryGenerator.generate(outfitDecision));
        } catch (RuntimeException exception) {
            log.warn(
                    "AI summary generation failed. fallback summary will be used. needUmbrella={}, recommendedOutfitLevel={}",
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
}
