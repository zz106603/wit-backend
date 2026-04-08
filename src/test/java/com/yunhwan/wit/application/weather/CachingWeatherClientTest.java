package com.yunhwan.wit.application.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingWeatherClientTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 현재날씨_캐시히트면_delegate를_다시_호출하지_않는다() {
        ResolvedLocation location = resolvedLocation();
        WeatherSnapshot snapshot = currentSnapshot();
        CountingWeatherClient delegate = new CountingWeatherClient(snapshot, forecastSnapshot());
        InMemoryWeatherCache cache = new InMemoryWeatherCache();
        cache.putCurrent(location, LocalDateTime.of(2026, 4, 1, 9, 0), snapshot);
        CachingWeatherClient weatherClient = new CachingWeatherClient(delegate, cache, clock);

        WeatherSnapshot result = weatherClient.fetchCurrentWeather(location);

        assertThat(result).isEqualTo(snapshot);
        assertThat(delegate.currentInvocationCount()).isZero();
    }

    @Test
    void 예보_캐시미스면_delegate결과를_캐시에_저장한다() {
        ResolvedLocation location = resolvedLocation();
        LocalDateTime targetTime = LocalDateTime.of(2026, 4, 1, 18, 0);
        WeatherSnapshot snapshot = forecastSnapshot();
        CountingWeatherClient delegate = new CountingWeatherClient(currentSnapshot(), snapshot);
        InMemoryWeatherCache cache = new InMemoryWeatherCache();
        CachingWeatherClient weatherClient = new CachingWeatherClient(delegate, cache, clock);

        WeatherSnapshot result = weatherClient.fetchWeatherAt(location, targetTime);

        assertThat(result).isEqualTo(snapshot);
        assertThat(delegate.forecastInvocationCount()).isEqualTo(1);
        assertThat(cache.findForecast(location, targetTime)).contains(snapshot);
    }

    @Test
    void 예보_range_캐시미스면_delegate_range를_한번만_호출하고_둘다_저장한다() {
        ResolvedLocation location = resolvedLocation();
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 1, 18, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 1, 21, 0);
        WeatherSnapshot startSnapshot = forecastSnapshot(startTime);
        WeatherSnapshot endSnapshot = forecastSnapshot(endTime);
        CountingWeatherClient delegate = new CountingWeatherClient(currentSnapshot(), startSnapshot, endSnapshot);
        InMemoryWeatherCache cache = new InMemoryWeatherCache();
        CachingWeatherClient weatherClient = new CachingWeatherClient(delegate, cache, clock);

        WeatherForecastSnapshots result = weatherClient.fetchWeatherRange(location, startTime, endTime);

        assertThat(result.startWeather()).isEqualTo(startSnapshot);
        assertThat(result.endWeather()).isEqualTo(endSnapshot);
        assertThat(delegate.forecastRangeInvocationCount()).isEqualTo(1);
        assertThat(delegate.forecastInvocationCount()).isZero();
        assertThat(cache.findForecast(location, startTime)).contains(startSnapshot);
        assertThat(cache.findForecast(location, endTime)).contains(endSnapshot);
    }

    private ResolvedLocation resolvedLocation() {
        return ResolvedLocation.resolved(
                "강남 회식",
                "강남",
                "서울특별시 강남구",
                37.4979,
                127.0276,
                0.9,
                LocationResolvedBy.RULE
        );
    }

    private WeatherSnapshot currentSnapshot() {
        return new WeatherSnapshot(
                "서울특별시 강남구",
                LocalDateTime.of(2026, 4, 1, 9, 0),
                18,
                17,
                20,
                WeatherType.CLEAR
        );
    }

    private WeatherSnapshot forecastSnapshot() {
        return forecastSnapshot(LocalDateTime.of(2026, 4, 1, 18, 0));
    }

    private WeatherSnapshot forecastSnapshot(LocalDateTime targetTime) {
        return new WeatherSnapshot(
                "서울특별시 강남구",
                targetTime,
                16,
                14,
                70,
                WeatherType.RAIN
        );
    }

    private static final class CountingWeatherClient implements WeatherClient {

        private final AtomicInteger currentInvocationCount = new AtomicInteger();
        private final AtomicInteger forecastInvocationCount = new AtomicInteger();
        private final AtomicInteger forecastRangeInvocationCount = new AtomicInteger();
        private final WeatherSnapshot currentSnapshot;
        private final WeatherSnapshot forecastSnapshot;
        private final WeatherSnapshot endForecastSnapshot;

        private CountingWeatherClient(WeatherSnapshot currentSnapshot, WeatherSnapshot forecastSnapshot) {
            this(currentSnapshot, forecastSnapshot, forecastSnapshot);
        }

        private CountingWeatherClient(
                WeatherSnapshot currentSnapshot,
                WeatherSnapshot forecastSnapshot,
                WeatherSnapshot endForecastSnapshot
        ) {
            this.currentSnapshot = currentSnapshot;
            this.forecastSnapshot = forecastSnapshot;
            this.endForecastSnapshot = endForecastSnapshot;
        }

        @Override
        public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
            currentInvocationCount.incrementAndGet();
            return currentSnapshot;
        }

        @Override
        public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
            forecastInvocationCount.incrementAndGet();
            return forecastSnapshot;
        }

        @Override
        public WeatherForecastSnapshots fetchWeatherRange(
                ResolvedLocation location,
                LocalDateTime startTime,
                LocalDateTime endTime
        ) {
            forecastRangeInvocationCount.incrementAndGet();
            return new WeatherForecastSnapshots(forecastSnapshot, endForecastSnapshot);
        }

        private int currentInvocationCount() {
            return currentInvocationCount.get();
        }

        private int forecastInvocationCount() {
            return forecastInvocationCount.get();
        }

        private int forecastRangeInvocationCount() {
            return forecastRangeInvocationCount.get();
        }
    }

    private static final class InMemoryWeatherCache implements WeatherCache {

        private final Map<String, WeatherSnapshot> store = new HashMap<>();

        @Override
        public Optional<WeatherSnapshot> findCurrent(ResolvedLocation location, LocalDateTime cacheTime) {
            return Optional.ofNullable(store.get("current:" + location.lat() + ":" + location.lng() + ":" + cacheTime));
        }

        @Override
        public Optional<WeatherSnapshot> findForecast(ResolvedLocation location, LocalDateTime targetTime) {
            return Optional.ofNullable(store.get("forecast:" + location.lat() + ":" + location.lng() + ":" + targetTime));
        }

        @Override
        public void putCurrent(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot) {
            store.put("current:" + location.lat() + ":" + location.lng() + ":" + cacheTime, weatherSnapshot);
        }

        @Override
        public void putForecast(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot) {
            store.put("forecast:" + location.lat() + ":" + location.lng() + ":" + targetTime, weatherSnapshot);
        }
    }
}
