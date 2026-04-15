package com.yunhwan.wit.domain.rule;

import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.util.Objects;

public class OutfitRuleEngine {

    public OutfitDecision decide(
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        Objects.requireNonNull(startWeather, "startWeather must not be null");
        Objects.requireNonNull(endWeather, "endWeather must not be null");

        boolean needUmbrella = shouldBringUmbrella(endWeather);
        Integer temperatureGap = currentWeather == null ? null : endWeather.feelsLike() - currentWeather.feelsLike();

        RecommendedOutfitLevel baselineLevel = determineBaseline(endWeather.feelsLike());
        boolean shouldAdjustWarmer = shouldAdjustWarmer(currentWeather, startWeather, endWeather);
        RecommendedOutfitLevel finalLevel = shouldAdjustWarmer ? increaseLevel(baselineLevel) : baselineLevel;

        return new OutfitDecision(
                needUmbrella,
                finalLevel,
                toOutfitText(finalLevel),
                buildUmbrellaReason(needUmbrella, endWeather),
                buildOutfitReason(baselineLevel, shouldAdjustWarmer, currentWeather, startWeather, endWeather),
                temperatureGap,
                buildWeatherChangeSummary(currentWeather, startWeather, endWeather),
                null
        );
    }

    private boolean shouldBringUmbrella(WeatherSnapshot endWeather) {
        return endWeather.precipitationProbability() >= 50 || endWeather.weatherType() == WeatherType.RAIN;
    }

    private RecommendedOutfitLevel determineBaseline(int endFeelsLike) {
        if (endFeelsLike >= 23) {
            return RecommendedOutfitLevel.SHORT_SLEEVE;
        }
        if (endFeelsLike >= 20) {
            return RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER;
        }
        if (endFeelsLike >= 17) {
            return RecommendedOutfitLevel.LONG_SLEEVE;
        }
        if (endFeelsLike >= 13) {
            return RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER;
        }
        return RecommendedOutfitLevel.HEAVY_OUTER;
    }

    private boolean shouldAdjustWarmer(
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        boolean muchColderThanCurrent = currentWeather != null
                && currentWeather.feelsLike() - endWeather.feelsLike() >= 4;
        boolean dropsFromStartToEnd = startWeather.feelsLike() - endWeather.feelsLike() >= 3;
        boolean lowTemperatureWithRain = endWeather.feelsLike() <= 12 && endWeather.weatherType() == WeatherType.RAIN;

        return muchColderThanCurrent || dropsFromStartToEnd || lowTemperatureWithRain;
    }

    private RecommendedOutfitLevel increaseLevel(RecommendedOutfitLevel level) {
        return switch (level) {
            case SHORT_SLEEVE -> RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER;
            case SHORT_SLEEVE_WITH_LIGHT_OUTER -> RecommendedOutfitLevel.LONG_SLEEVE;
            case LONG_SLEEVE -> RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER;
            case LONG_SLEEVE_WITH_LIGHT_OUTER, HEAVY_OUTER -> RecommendedOutfitLevel.HEAVY_OUTER;
        };
    }

    private String toOutfitText(RecommendedOutfitLevel level) {
        return switch (level) {
            case SHORT_SLEEVE -> "반팔";
            case SHORT_SLEEVE_WITH_LIGHT_OUTER -> "반팔 + 얇은 겉옷";
            case LONG_SLEEVE -> "긴팔";
            case LONG_SLEEVE_WITH_LIGHT_OUTER -> "긴팔 + 가벼운 겉옷";
            case HEAVY_OUTER -> "두꺼운 겉옷";
        };
    }

    private String buildUmbrellaReason(boolean needUmbrella, WeatherSnapshot endWeather) {
        if (needUmbrella) {
            if (endWeather.weatherType() == WeatherType.RAIN) {
                return "종료 시점 비 예보가 있어 우산이 필요합니다.";
            }
            return "종료 시점 강수 확률이 50% 이상이라 우산이 필요합니다.";
        }
        return "종료 시점 비 가능성이 낮아 우산은 없어도 됩니다.";
    }

    private String buildOutfitReason(
            RecommendedOutfitLevel baselineLevel,
            boolean adjusted,
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        if (!adjusted) {
            return "종료 시점 체감온도 기준으로 " + toOutfitText(baselineLevel) + "을 추천합니다.";
        }

        if (currentWeather != null && currentWeather.feelsLike() - endWeather.feelsLike() >= 4) {
            return "도착 시점 체감온도가 현재보다 4도 이상 낮아 한 단계 더 따뜻하게 추천합니다.";
        }
        if (startWeather.feelsLike() - endWeather.feelsLike() >= 3) {
            return "시작 대비 종료 시점 체감온도가 3도 이상 내려가 한 단계 더 따뜻하게 추천합니다.";
        }
        return "낮은 체감온도와 비를 함께 고려해 한 단계 더 따뜻하게 추천합니다.";
    }

    private String buildWeatherChangeSummary(
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        Integer currentToEndGap = currentWeather == null ? null : endWeather.feelsLike() - currentWeather.feelsLike();
        int startToEndGap = endWeather.feelsLike() - startWeather.feelsLike();

        if (currentToEndGap != null && currentToEndGap <= -4) {
            return "현재보다 종료 시점이 더 쌀쌀합니다.";
        }
        if (startToEndGap <= -3) {
            return "일정이 진행될수록 기온이 내려갑니다.";
        }
        if (endWeather.weatherType() == WeatherType.RAIN) {
            return "종료 시점에는 비를 고려해야 합니다.";
        }
        return "큰 날씨 변화는 없습니다.";
    }
}
