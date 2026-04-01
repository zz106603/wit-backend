package com.yunhwan.wit.infrastructure.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WeatherSnapshotMapper {

    public WeatherSnapshot toSnapshot(
            ResolvedLocation location,
            LocalDateTime requestedTargetTime,
            WeatherApiResponse response
    ) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(requestedTargetTime, "requestedTargetTime must not be null");
        Objects.requireNonNull(response, "response must not be null");

        if (response.temperature() == null) {
            throw new IllegalArgumentException("temperature must not be null");
        }
        if (response.feelsLike() == null) {
            throw new IllegalArgumentException("feelsLike must not be null");
        }
        if (response.precipitationProbability() == null) {
            throw new IllegalArgumentException("precipitationProbability must not be null");
        }

        String regionName = StringUtils.hasText(response.regionName())
                ? response.regionName()
                : location.displayLocation();
        LocalDateTime targetTime = response.targetTime() != null
                ? response.targetTime()
                : requestedTargetTime;

        return new WeatherSnapshot(
                regionName,
                targetTime,
                response.temperature(),
                response.feelsLike(),
                response.precipitationProbability(),
                mapWeatherType(response.condition())
        );
    }

    private WeatherType mapWeatherType(String condition) {
        if (!StringUtils.hasText(condition)) {
            throw new IllegalArgumentException("condition must not be blank");
        }

        String normalized = condition
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "CLEAR", "SUNNY" -> WeatherType.CLEAR;
            case "CLOUDY", "PARTLY_CLOUDY", "OVERCAST" -> WeatherType.CLOUDY;
            case "RAIN", "DRIZZLE", "SHOWER", "THUNDERSTORM" -> WeatherType.RAIN;
            case "SNOW", "SLEET", "BLIZZARD" -> WeatherType.SNOW;
            case "WIND", "WINDY", "STRONG_WIND", "GALE" -> WeatherType.STRONG_WIND;
            default -> throw new IllegalArgumentException("unsupported weather condition: " + condition);
        };
    }
}
