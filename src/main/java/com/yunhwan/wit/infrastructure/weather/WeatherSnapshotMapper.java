package com.yunhwan.wit.infrastructure.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.temporal.ChronoUnit;
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
            return toOpenMeteoSnapshot(location, requestedTargetTime, response);
        }

        return toSnapshot(
                location,
                requestedTargetTime,
                response.temperature(),
                response.feelsLike(),
                response.precipitationProbability(),
                response.condition(),
                response.regionName()
        );
    }

    private WeatherSnapshot toOpenMeteoSnapshot(
            ResolvedLocation location,
            LocalDateTime requestedTargetTime,
            WeatherApiResponse response
    ) {
        if (response.current() != null) {
            return toSnapshot(
                    location,
                    requestedTargetTime,
                    toInteger(response.current().temperature(), "temperature"),
                    toInteger(response.current().apparentTemperature(), "feelsLike"),
                    currentPrecipitationProbability(response.current()),
                    mapWeatherCode(response.current().weatherCode()).name(),
                    null
            );
        }

        if (response.hourly() != null) {
            int index = findHourlyIndex(response.hourly(), requestedTargetTime);
            return toSnapshot(
                    location,
                    requestedTargetTime,
                    toInteger(valueAt(response.hourly().temperature(), index), "temperature"),
                    toInteger(valueAt(response.hourly().apparentTemperature(), index), "feelsLike"),
                    valueAt(response.hourly().precipitationProbability(), index),
                    mapWeatherCode(valueAt(response.hourly().weatherCode(), index)).name(),
                    null
            );
        }

        throw new IllegalArgumentException("temperature must not be null");
    }

    private WeatherSnapshot toSnapshot(
            ResolvedLocation location,
            LocalDateTime requestedTargetTime,
            Integer temperature,
            Integer feelsLike,
            Integer precipitationProbability,
            String condition,
            String responseRegionName
    ) {
        if (temperature == null) {
            throw new IllegalArgumentException("temperature must not be null");
        }
        if (feelsLike == null) {
            throw new IllegalArgumentException("feelsLike must not be null");
        }
        if (precipitationProbability == null) {
            throw new IllegalArgumentException("precipitationProbability must not be null");
        }

        String regionName = StringUtils.hasText(responseRegionName)
                ? responseRegionName
                : location.displayLocation();

        return new WeatherSnapshot(
                regionName,
                requestedTargetTime,
                temperature,
                feelsLike,
                precipitationProbability,
                mapWeatherType(condition)
        );
    }

    private int findHourlyIndex(WeatherApiResponse.HourlyWeather hourly, LocalDateTime requestedTargetTime) {
        if (hourly.time() == null || hourly.time().isEmpty()) {
            throw new IllegalArgumentException("hourly time must not be empty");
        }

        LocalDateTime requestedHour = requestedTargetTime.truncatedTo(ChronoUnit.HOURS);
        for (int index = 0; index < hourly.time().size(); index++) {
            if (requestedHour.equals(hourly.time().get(index))) {
                return index;
            }
        }

        throw new IllegalArgumentException("requested target time is not included in hourly weather");
    }

    private Integer currentPrecipitationProbability(WeatherApiResponse.CurrentWeather current) {
        if (current.precipitationProbability() != null) {
            return current.precipitationProbability();
        }

        double precipitation = current.precipitation() == null ? 0.0 : current.precipitation();
        double rain = current.rain() == null ? 0.0 : current.rain();
        return precipitation > 0.0 || rain > 0.0 ? 100 : 0;
    }

    private Integer toInteger(Double value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }

        return (int) Math.round(value);
    }

    private <T> T valueAt(java.util.List<T> values, int index) {
        if (values == null || values.size() <= index) {
            return null;
        }

        return values.get(index);
    }

    private WeatherType mapWeatherType(String condition) {
        if (!StringUtils.hasText(condition)) {
            return WeatherType.UNKNOWN;
        }

        String normalized = condition.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "CLEAR" -> WeatherType.CLEAR;
            case "CLOUDS", "CLOUDY" -> WeatherType.CLOUDY;
            case "RAIN" -> WeatherType.RAIN;
            case "SNOW" -> WeatherType.SNOW;
            case "WIND", "STRONG_WIND" -> WeatherType.STRONG_WIND;
            default -> WeatherType.UNKNOWN;
        };
    }

    private WeatherType mapWeatherCode(Integer weatherCode) {
        if (weatherCode == null) {
            return WeatherType.UNKNOWN;
        }

        return switch (weatherCode) {
            case 0 -> WeatherType.CLEAR;
            case 1, 2, 3, 45, 48 -> WeatherType.CLOUDY;
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherType.RAIN;
            case 71, 73, 75, 77, 85, 86 -> WeatherType.SNOW;
            default -> WeatherType.UNKNOWN;
        };
    }
}
