package com.yunhwan.wit.infrastructure.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.weather.WeatherCache;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisWeatherCache implements WeatherCache {

    private static final Logger log = LoggerFactory.getLogger(RedisWeatherCache.class);
    private static final String KEY_PREFIX = "weather:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WeatherCacheProperties properties;

    public RedisWeatherCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WeatherCacheProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Optional<WeatherSnapshot> findCurrent(ResolvedLocation location, LocalDateTime cacheTime) {
        return find(toKey("current", location, cacheTime));
    }

    @Override
    public Optional<WeatherSnapshot> findForecast(ResolvedLocation location, LocalDateTime targetTime) {
        return find(toKey("forecast", location, targetTime));
    }

    @Override
    public Optional<WeatherSnapshot> findLatestCurrent(ResolvedLocation location) {
        return findLatest("current", location);
    }

    @Override
    public Optional<WeatherSnapshot> findLatestForecast(ResolvedLocation location) {
        return findLatest("forecast", location);
    }

    @Override
    public void putCurrent(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot) {
        put(toKey("current", location, cacheTime), weatherSnapshot, ttlForCurrent());
        putLatestIfNewer("current", location, weatherSnapshot, ttlForCurrent());
    }

    @Override
    public void putForecast(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot) {
        put(toKey("forecast", location, targetTime), weatherSnapshot, ttlForForecast());
        putLatestIfNewer("forecast", location, weatherSnapshot, ttlForForecast());
    }

    private Optional<WeatherSnapshot> find(String key) {
        log.info("[RecommendationDebug] Redis weather find before. key={}", key);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            log.info("[RecommendationDebug] Redis weather find after. key={}, hit=false", key);
            return Optional.empty();
        }

        try {
            Optional<WeatherSnapshot> result = Optional.of(objectMapper.readValue(payload, WeatherSnapshot.class));
            log.info("[RecommendationDebug] Redis weather find after. key={}, hit=true", key);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize cached weather snapshot", exception);
        }
    }

    private Optional<WeatherSnapshot> findLatest(String weatherType, ResolvedLocation location) {
        String latestKey = toLatestKey(weatherType, location);
        log.info("[RecommendationDebug] Redis weather latest find before. key={}", latestKey);
        Optional<WeatherSnapshot> result = find(latestKey);
        log.info("[RecommendationDebug] Redis weather latest find after. key={}, hit={}", latestKey, result.isPresent());
        return result;
    }

    private void put(String key, WeatherSnapshot weatherSnapshot, Duration ttl) {
        try {
            log.info("[RecommendationDebug] Redis weather put before. key={}", key);
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(weatherSnapshot),
                    ttl
            );
            log.info("[RecommendationDebug] Redis weather put after. key={}", key);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize weather snapshot for cache", exception);
        }
    }

    private void putLatestIfNewer(
            String weatherType,
            ResolvedLocation location,
            WeatherSnapshot weatherSnapshot,
            Duration ttl
    ) {
        String latestKey = toLatestKey(weatherType, location);
        Optional<WeatherSnapshot> currentLatest = find(latestKey);
        if (currentLatest.isPresent() && currentLatest.get().targetTime().isAfter(weatherSnapshot.targetTime())) {
            log.info(
                    "[RecommendationDebug] Redis weather latest put skipped. key={}, existingTargetTime={}, newTargetTime={}",
                    latestKey,
                    currentLatest.get().targetTime(),
                    weatherSnapshot.targetTime()
            );
            return;
        }
        put(latestKey, weatherSnapshot, ttl);
    }

    private String toKey(String weatherType, ResolvedLocation location, LocalDateTime targetTime) {
        log.info(
                "[RecommendationDebug] Redis weather key generation start. type={}, location={}, targetTime={}",
                weatherType,
                location.displayLocation(),
                targetTime
        );
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(targetTime, "targetTime must not be null");

        if (location.lat() == null || location.lng() == null) {
            throw new IllegalArgumentException("location coordinates must not be null");
        }

        String key = KEY_PREFIX
                + weatherType.toLowerCase(Locale.ROOT)
                + ":"
                + location.lat()
                + ":"
                + location.lng()
                + ":"
                + TIME_FORMATTER.format(targetTime);
        log.info("[RecommendationDebug] Redis weather key generation end. key={}", key);
        return key;
    }

    private String toLatestKey(String weatherType, ResolvedLocation location) {
        Objects.requireNonNull(location, "location must not be null");

        if (location.lat() == null || location.lng() == null) {
            throw new IllegalArgumentException("location coordinates must not be null");
        }

        String key = KEY_PREFIX
                + "latest:"
                + weatherType.toLowerCase(Locale.ROOT)
                + ":"
                + location.lat()
                + ":"
                + location.lng();
        log.info("[RecommendationDebug] Redis weather latest key generation end. key={}", key);
        return key;
    }

    private Duration ttlForCurrent() {
        return properties.ttl();
    }

    private Duration ttlForForecast() {
        return properties.ttl();
    }
}
