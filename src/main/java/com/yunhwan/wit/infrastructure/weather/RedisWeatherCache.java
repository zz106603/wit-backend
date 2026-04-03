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
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisWeatherCache implements WeatherCache {

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
    public void putCurrent(ResolvedLocation location, LocalDateTime cacheTime, WeatherSnapshot weatherSnapshot) {
        put(toKey("current", location, cacheTime), weatherSnapshot, ttlForCurrent());
    }

    @Override
    public void putForecast(ResolvedLocation location, LocalDateTime targetTime, WeatherSnapshot weatherSnapshot) {
        put(toKey("forecast", location, targetTime), weatherSnapshot, ttlForForecast());
    }

    private Optional<WeatherSnapshot> find(String key) {
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, WeatherSnapshot.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize cached weather snapshot", exception);
        }
    }

    private void put(String key, WeatherSnapshot weatherSnapshot, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(weatherSnapshot),
                    ttl
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize weather snapshot for cache", exception);
        }
    }

    private String toKey(String weatherType, ResolvedLocation location, LocalDateTime targetTime) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(targetTime, "targetTime must not be null");

        if (location.lat() == null || location.lng() == null) {
            throw new IllegalArgumentException("location coordinates must not be null");
        }

        return KEY_PREFIX
                + weatherType.toLowerCase(Locale.ROOT)
                + ":"
                + location.lat()
                + ":"
                + location.lng()
                + ":"
                + TIME_FORMATTER.format(targetTime);
    }

    private Duration ttlForCurrent() {
        return properties.ttl();
    }

    private Duration ttlForForecast() {
        return properties.ttl();
    }
}
