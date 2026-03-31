package com.yunhwan.wit.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record WeatherSnapshot(
        String regionName,
        LocalDateTime targetTime,
        int temperature,
        int feelsLike,
        int precipitationProbability,
        WeatherType weatherType
) {

    public WeatherSnapshot {
        Objects.requireNonNull(regionName, "regionName must not be null");
        Objects.requireNonNull(targetTime, "targetTime must not be null");
        Objects.requireNonNull(weatherType, "weatherType must not be null");

        if (precipitationProbability < 0 || precipitationProbability > 100) {
            throw new IllegalArgumentException("precipitationProbability must be between 0 and 100");
        }
    }
}
