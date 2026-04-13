package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.LocalDateTime;
import java.util.List;

public record HomeRecommendationResponse(
        List<HomeRecommendationItemResponse> recommendations
) {

    public static HomeRecommendationResponse from(List<RecommendationResult> results) {
        return new HomeRecommendationResponse(results.stream()
                .map(HomeRecommendationItemResponse::from)
                .toList());
    }

    public record HomeRecommendationItemResponse(
            String eventId,
            String title,
            String rawLocation,
            LocationResponse resolvedLocation,
            boolean weatherFallbackApplied,
            boolean needUmbrella,
            String recommendedOutfitText,
            String aiSummary,
            String umbrellaReason,
            String outfitReason,
            Integer temperatureGap,
            String weatherChangeSummary,
            WeatherSnapshotResponse currentWeather,
            WeatherSnapshotResponse startWeather,
            WeatherSnapshotResponse endWeather
    ) {

        private static HomeRecommendationItemResponse from(RecommendationResult result) {
            OutfitDecision decision = result.outfitDecision();
            return new HomeRecommendationItemResponse(
                    result.calendarEvent().eventId(),
                    result.calendarEvent().title(),
                    result.calendarEvent().rawLocation(),
                    LocationResponse.from(result.resolvedLocation()),
                    result.weatherFallbackApplied(),
                    decision.needUmbrella(),
                    decision.recommendedOutfitText(),
                    decision.aiSummary(),
                    decision.umbrellaReason(),
                    decision.outfitReason(),
                    decision.temperatureGap(),
                    decision.weatherChangeSummary(),
                    WeatherSnapshotResponse.from(result.currentWeather()),
                    WeatherSnapshotResponse.from(result.startWeather()),
                    WeatherSnapshotResponse.from(result.endWeather())
            );
        }
    }

    public record LocationResponse(
            String rawLocation,
            String normalizedQuery,
            String displayLocation,
            Double lat,
            Double lng,
            Double confidence,
            String status,
            String resolvedBy
    ) {

        private static LocationResponse from(ResolvedLocation location) {
            if (location == null) {
                return null;
            }

            return new LocationResponse(
                    location.rawLocation(),
                    location.normalizedQuery(),
                    location.displayLocation(),
                    location.lat(),
                    location.lng(),
                    location.confidence(),
                    location.status().name(),
                    location.resolvedBy() == null ? null : location.resolvedBy().name()
            );
        }
    }

    public record WeatherSnapshotResponse(
            String regionName,
            LocalDateTime targetTime,
            int temperature,
            int feelsLike,
            int precipitationProbability,
            String weatherType
    ) {

        private static WeatherSnapshotResponse from(WeatherSnapshot weatherSnapshot) {
            if (weatherSnapshot == null) {
                return null;
            }

            return new WeatherSnapshotResponse(
                    weatherSnapshot.regionName(),
                    weatherSnapshot.targetTime(),
                    weatherSnapshot.temperature(),
                    weatherSnapshot.feelsLike(),
                    weatherSnapshot.precipitationProbability(),
                    weatherSnapshot.weatherType().name()
            );
        }
    }
}
