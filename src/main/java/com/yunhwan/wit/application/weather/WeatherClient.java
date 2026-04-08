package com.yunhwan.wit.application.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.LocalDateTime;

public interface WeatherClient {

    WeatherSnapshot fetchCurrentWeather(ResolvedLocation location);

    WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime);

    default WeatherForecastSnapshots fetchWeatherRange(
            ResolvedLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return new WeatherForecastSnapshots(
                fetchWeatherAt(location, startTime),
                fetchWeatherAt(location, endTime)
        );
    }
}
