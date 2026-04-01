package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import java.time.LocalDateTime;
import java.util.Objects;

public class RecommendationService {

    private static final int SAFE_FALLBACK_TEMPERATURE = 12;
    private static final int SAFE_FALLBACK_FEELS_LIKE = 12;
    private static final int SAFE_FALLBACK_PRECIPITATION = 100;

    private final LocationResolver locationResolver;
    private final CurrentLocationProvider currentLocationProvider;
    private final WeatherClient weatherClient;
    private final OutfitRuleEngine outfitRuleEngine;

    public RecommendationService(
            LocationResolver locationResolver,
            CurrentLocationProvider currentLocationProvider,
            WeatherClient weatherClient,
            OutfitRuleEngine outfitRuleEngine
    ) {
        this.locationResolver = Objects.requireNonNull(locationResolver, "locationResolver must not be null");
        this.currentLocationProvider = Objects.requireNonNull(
                currentLocationProvider,
                "currentLocationProvider must not be null"
        );
        this.weatherClient = Objects.requireNonNull(weatherClient, "weatherClient must not be null");
        this.outfitRuleEngine = Objects.requireNonNull(outfitRuleEngine, "outfitRuleEngine must not be null");
    }

    public RecommendationResult recommend(CalendarEvent calendarEvent) {
        Objects.requireNonNull(calendarEvent, "calendarEvent must not be null");

        ResolvedLocation currentLocation = currentLocationProvider.getCurrentLocation();
        ResolvedLocation resolvedLocation = resolveEventLocation(calendarEvent, currentLocation);

        WeatherSnapshot currentWeather = fetchCurrentWeather(currentLocation);
        WeatherSnapshot startWeather = fetchWeatherAt(resolvedLocation, calendarEvent.startAt());
        WeatherSnapshot endWeather = fetchWeatherAt(resolvedLocation, calendarEvent.endAt());

        OutfitDecision outfitDecision = outfitRuleEngine.decide(currentWeather, startWeather, endWeather);

        return new RecommendationResult(
                calendarEvent,
                resolvedLocation,
                currentWeather,
                startWeather,
                endWeather,
                outfitDecision
        );
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
        LocalDateTime requestedTargetTime = LocalDateTime.now();

        try {
            WeatherSnapshot snapshot = weatherClient.fetchCurrentWeather(location);
            return snapshot != null ? snapshot : safeFallbackSnapshot(location, requestedTargetTime);
        } catch (RuntimeException exception) {
            return safeFallbackSnapshot(location, requestedTargetTime);
        }
    }

    private WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
        try {
            WeatherSnapshot snapshot = weatherClient.fetchWeatherAt(location, targetTime);
            return snapshot != null ? snapshot : safeFallbackSnapshot(location, targetTime);
        } catch (RuntimeException exception) {
            return safeFallbackSnapshot(location, targetTime);
        }
    }

    private WeatherSnapshot safeFallbackSnapshot(
            ResolvedLocation location,
            LocalDateTime targetTime
    ) {
        return new WeatherSnapshot(
                location.displayLocation(),
                targetTime,
                SAFE_FALLBACK_TEMPERATURE,
                SAFE_FALLBACK_FEELS_LIKE,
                SAFE_FALLBACK_PRECIPITATION,
                WeatherType.UNKNOWN
        );
    }
}
