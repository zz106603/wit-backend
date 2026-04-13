package com.yunhwan.wit.application.weather;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.LocalDateTime;
import java.util.Optional;

public interface WeatherCache {

    Optional<WeatherSnapshot> findCurrent(ResolvedLocation location, LocalDateTime cacheTime);

    Optional<WeatherSnapshot> findForecast(ResolvedLocation location, LocalDateTime targetTime);

    default Optional<WeatherSnapshot> findLatestCurrent(ResolvedLocation location) {
        return Optional.empty();
    }

    default Optional<WeatherSnapshot> findLatestForecast(ResolvedLocation location) {
        return Optional.empty();
    }

    void putCurrent(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot);

    void putForecast(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot);
}
