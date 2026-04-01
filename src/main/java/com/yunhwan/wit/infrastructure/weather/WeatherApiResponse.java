package com.yunhwan.wit.infrastructure.weather;

import java.time.LocalDateTime;

public record WeatherApiResponse(
        String regionName,
        LocalDateTime targetTime,
        Integer temperature,
        Integer feelsLike,
        Integer precipitationProbability,
        String condition
) {
}
