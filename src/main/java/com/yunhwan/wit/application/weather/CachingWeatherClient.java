package com.yunhwan.wit.application.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(CachingWeatherClient.class);

    private final WeatherClient delegate;
    private final WeatherCache weatherCache;
    private final Clock clock;

    public CachingWeatherClient(
            WeatherClient delegate,
            WeatherCache weatherCache,
            Clock clock
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.weatherCache = Objects.requireNonNull(weatherCache, "weatherCache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
        LocalDateTime cacheTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        WeatherSnapshot cached = readCurrentFromCache(location, cacheTime);
        if (cached != null) {
            return cached;
        }

        WeatherSnapshot weatherSnapshot = delegate.fetchCurrentWeather(location);
        writeCurrentToCache(location, cacheTime, weatherSnapshot);
        return weatherSnapshot;
    }

    @Override
    public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
        WeatherSnapshot cached = readForecastFromCache(location, targetTime);
        if (cached != null) {
            return cached;
        }

        WeatherSnapshot weatherSnapshot = delegate.fetchWeatherAt(location, targetTime);
        writeForecastToCache(location, targetTime, weatherSnapshot);
        return weatherSnapshot;
    }

    @Override
    public WeatherForecastSnapshots fetchWeatherRange(
            ResolvedLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        WeatherSnapshot cachedStart = readForecastFromCache(location, startTime);
        WeatherSnapshot cachedEnd = readForecastFromCache(location, endTime);
        if (cachedStart != null && cachedEnd != null) {
            return new WeatherForecastSnapshots(cachedStart, cachedEnd);
        }

        if (cachedStart == null && cachedEnd == null) {
            WeatherForecastSnapshots snapshots = delegate.fetchWeatherRange(location, startTime, endTime);
            writeForecastToCache(location, startTime, snapshots.startWeather());
            writeForecastToCache(location, endTime, snapshots.endWeather());
            return snapshots;
        }

        WeatherSnapshot startWeather = cachedStart != null ? cachedStart : fetchWeatherAt(location, startTime);
        WeatherSnapshot endWeather = cachedEnd != null ? cachedEnd : fetchWeatherAt(location, endTime);
        return new WeatherForecastSnapshots(startWeather, endWeather);
    }

    private WeatherSnapshot readCurrentFromCache(ResolvedLocation location, LocalDateTime cacheTime) {
        try {
            return weatherCache.findCurrent(location, cacheTime).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Weather cache read failed for current weather. location={}", location.displayLocation(), exception);
            return null;
        }
    }

    private WeatherSnapshot readForecastFromCache(ResolvedLocation location, LocalDateTime targetTime) {
        try {
            return weatherCache.findForecast(location, targetTime).orElse(null);
        } catch (RuntimeException exception) {
            log.warn(
                    "Weather cache read failed for forecast weather. location={}, targetTime={}",
                    location.displayLocation(),
                    targetTime,
                    exception
            );
            return null;
        }
    }

    private void writeCurrentToCache(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot) {
        try {
            weatherCache.putCurrent(location, cacheTime, weatherSnapshot);
        } catch (RuntimeException exception) {
            log.warn("Weather cache write failed for current weather. location={}", location.displayLocation(), exception);
        }
    }

    private void writeForecastToCache(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot) {
        try {
            weatherCache.putForecast(location, targetTime, weatherSnapshot);
        } catch (RuntimeException exception) {
            log.warn(
                    "Weather cache write failed for forecast weather. location={}, targetTime={}",
                    location.displayLocation(),
                    targetTime,
                    exception
            );
        }
    }
}
