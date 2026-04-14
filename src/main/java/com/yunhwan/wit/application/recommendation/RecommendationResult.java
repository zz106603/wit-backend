package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;

public record RecommendationResult(
        OutfitDecision outfitDecision,
        CalendarEvent calendarEvent,
        ResolvedLocation resolvedLocation,
        WeatherSnapshot currentWeather,
        WeatherSnapshot startWeather,
        WeatherSnapshot endWeather,
        boolean weatherFallbackApplied,
        boolean locationFallbackApplied,
        RecommendationWeatherSource weatherSource
) {
}
