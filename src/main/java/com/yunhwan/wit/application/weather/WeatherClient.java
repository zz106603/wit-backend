package com.yunhwan.wit.application.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.LocalDateTime;
import java.util.Objects;

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

    default CurrentWeatherResult fetchCurrentWeatherResult(ResolvedLocation location) {
        return new CurrentWeatherResult(fetchCurrentWeather(location), WeatherFetchSource.NORMAL);
    }

    default WeatherRangeResult fetchWeatherRangeResult(
            ResolvedLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return new WeatherRangeResult(fetchWeatherRange(location, startTime, endTime), WeatherFetchSource.NORMAL);
    }

    enum WeatherFetchSource {
        NORMAL,
        CACHE
    }

    record CurrentWeatherResult(
            WeatherSnapshot snapshot,
            WeatherFetchSource source
    ) {

        public CurrentWeatherResult {
            Objects.requireNonNull(source, "source must not be null");
        }
    }

    record WeatherRangeResult(
            WeatherForecastSnapshots snapshots,
            WeatherFetchSource source
    ) {

        public WeatherRangeResult {
            Objects.requireNonNull(source, "source must not be null");
        }
    }
}
