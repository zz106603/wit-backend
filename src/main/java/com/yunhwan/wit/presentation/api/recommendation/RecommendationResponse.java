package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "추천 응답")
public record RecommendationResponse(
        @Schema(description = "일정 ID", example = "event-1")
        String eventId,
        @Schema(description = "일정 제목", example = "강남 회식")
        String title,
        @Schema(description = "일정 시작 시각", example = "2026-04-07T18:00:00")
        LocalDateTime startAt,
        @Schema(description = "일정 종료 시각", example = "2026-04-07T21:00:00")
        LocalDateTime endAt,
        @Schema(description = "표시용 위치", example = "서울특별시 강남구 테헤란로 1")
        String location,
        @Schema(description = "우산 필요 여부", example = "true")
        boolean needUmbrella,
        @Schema(description = "추천 옷차림 문구", example = "긴팔 + 가벼운 겉옷")
        String recommendedOutfitText,
        @Schema(description = "최종 요약 문구", example = "종료 시점 비 예보가 있어 우산과 긴팔 + 가벼운 겉옷이 필요합니다.")
        String summary,
        @Schema(description = "위치 fallback 적용 여부", example = "false")
        boolean locationFallbackApplied,
        @Schema(description = "날씨 fallback 적용 여부", example = "false")
        boolean weatherFallbackApplied,
        @Schema(description = "날씨 데이터 출처", example = "NORMAL", allowableValues = {"NORMAL", "CACHE", "SAFE_DEFAULT"})
        String weatherSource,
        @Schema(description = "fallback 안내 문구", example = "실시간 날씨 대신 최신 캐시 데이터를 사용했습니다.", nullable = true)
        String fallbackNotice,
        @Schema(description = "원본 위치 문자열", example = "강남 목구멍", nullable = true)
        String rawLocation,
        @Schema(description = "위치 해석 상세")
        LocationResolutionResponse locationResolution,
        @Schema(description = "우산 판단 이유", example = "종료 시점 비 예보가 있어 우산이 필요합니다.", nullable = true)
        String umbrellaReason,
        @Schema(description = "옷차림 판단 이유", example = "종료 시점 체감온도 기준으로 긴팔 + 가벼운 겉옷을 추천합니다.", nullable = true)
        String outfitReason,
        @Schema(description = "현재 대비 체감온도 차이", example = "-6", nullable = true)
        Integer temperatureGap,
        @Schema(description = "날씨 변화 요약", example = "현재보다 종료 시점이 더 쌀쌀합니다.", nullable = true)
        String weatherChangeSummary,
        @Schema(description = "현재 시점 날씨", nullable = true)
        WeatherSnapshotResponse currentWeather,
        @Schema(description = "일정 시작 시점 날씨", nullable = true)
        WeatherSnapshotResponse startWeather,
        @Schema(description = "일정 종료 시점 날씨", nullable = true)
        WeatherSnapshotResponse endWeather
) {

    public static RecommendationResponse from(RecommendationResult result) {
        OutfitDecision decision = result.outfitDecision();

        return new RecommendationResponse(
                result.calendarEvent().eventId(),
                result.calendarEvent().title(),
                result.calendarEvent().startAt(),
                result.calendarEvent().endAt(),
                result.resolvedLocation() == null ? null : result.resolvedLocation().displayLocation(),
                decision.needUmbrella(),
                decision.recommendedOutfitText(),
                decision.aiSummary(),
                result.locationFallbackApplied(),
                result.weatherFallbackApplied(),
                result.weatherSource().name(),
                fallbackNotice(result),
                result.calendarEvent().rawLocation(),
                LocationResolutionResponse.from(result.resolvedLocation()),
                decision.umbrellaReason(),
                decision.outfitReason(),
                decision.temperatureGap(),
                decision.weatherChangeSummary(),
                WeatherSnapshotResponse.from(result.currentWeather()),
                WeatherSnapshotResponse.from(result.startWeather()),
                WeatherSnapshotResponse.from(result.endWeather())
        );
    }

    private static String fallbackNotice(RecommendationResult result) {
        if (result.weatherFallbackApplied()) {
            return "날씨 조회 실패로 안전 기본 추천을 반환했습니다.";
        }
        if ("CACHE".equals(result.weatherSource().name())) {
            return "실시간 날씨 대신 최신 캐시 데이터를 사용했습니다.";
        }
        if (result.locationFallbackApplied()) {
            return "일정 위치 해석에 실패해 현재 위치 기준으로 추천했습니다.";
        }
        return null;
    }

    @Schema(description = "위치 해석 상세")
    public record LocationResolutionResponse(
            @Schema(description = "원본 위치 문자열", example = "강남 목구멍", nullable = true)
            String rawLocation,
            @Schema(description = "정규화 질의", example = "목구멍 강남점", nullable = true)
            String normalizedQuery,
            @Schema(description = "표시용 위치", example = "서울특별시 강남구 테헤란로 1", nullable = true)
            String displayLocation,
            @Schema(description = "위도", example = "37.5001", nullable = true)
            Double lat,
            @Schema(description = "경도", example = "127.0362", nullable = true)
            Double lng,
            @Schema(description = "신뢰도", example = "0.85", nullable = true)
            Double confidence,
            @Schema(description = "해석 상태", example = "RESOLVED", allowableValues = {"RESOLVED", "APPROXIMATED", "FAILED"})
            String status,
            @Schema(description = "해석 출처", example = "GOOGLE_PLACES", nullable = true)
            String resolvedBy
    ) {
        private static LocationResolutionResponse from(ResolvedLocation location) {
            if (location == null) {
                return null;
            }

            return new LocationResolutionResponse(
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

    @Schema(description = "날씨 스냅샷")
    public record WeatherSnapshotResponse(
            @Schema(description = "지역명", example = "서울특별시 강남구 테헤란로 1")
            String regionName,
            @Schema(description = "대상 시각", example = "2026-04-07T21:00:00")
            LocalDateTime targetTime,
            @Schema(description = "기온", example = "16")
            int temperature,
            @Schema(description = "체감온도", example = "14")
            int feelsLike,
            @Schema(description = "강수 확률", example = "70")
            int precipitationProbability,
            @Schema(description = "날씨 타입", example = "RAIN")
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
