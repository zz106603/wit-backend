package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import java.util.Objects;

public class RecommendationService {

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

        if (currentWeather == null || startWeather == null || endWeather == null) {
            return new RecommendationResult(
                    safeDefaultDecision(),
                    calendarEvent,
                    resolvedLocation,
                    null,
                    null,
                    null,
                    true
            );
        }

        OutfitDecision outfitDecision = outfitRuleEngine.decide(currentWeather, startWeather, endWeather);

        return new RecommendationResult(
                outfitDecision,
                calendarEvent,
                resolvedLocation,
                currentWeather,
                startWeather,
                endWeather,
                false
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

    private OutfitDecision safeDefaultDecision() {
        return new OutfitDecision(
                true,
                RecommendedOutfitLevel.HEAVY_OUTER,
                "두꺼운 겉옷",
                "날씨 정보를 확인할 수 없어 우산을 챙기는 보수적 추천을 제공합니다.",
                "날씨 정보를 가져오지 못해 안전 기준으로 두꺼운 겉옷을 추천합니다.",
                0,
                "날씨 정보를 가져오지 못해 안전 기본 추천으로 대체되었습니다.",
                null
        );
    }
}
