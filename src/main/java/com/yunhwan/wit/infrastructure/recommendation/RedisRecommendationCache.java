package com.yunhwan.wit.infrastructure.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.recommendation.RecommendationCache;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRecommendationCache implements RecommendationCache {

    private static final String KEY_PREFIX = "recommendation:";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationCacheProperties properties;

    public RedisRecommendationCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RecommendationCacheProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Optional<RecommendationResult> find(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
        return find(toKey(calendarEvent, cacheTime));
    }

    @Override
    public void put(CalendarEvent calendarEvent, LocalDateTime cacheTime, RecommendationResult recommendationResult) {
        String key = toKey(calendarEvent, cacheTime);
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(recommendationResult),
                    properties.ttl()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize recommendation result for cache", exception);
        }
    }

    private Optional<RecommendationResult> find(String key) {
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, RecommendationResult.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize cached recommendation result", exception);
        }
    }

    private String toKey(CalendarEvent calendarEvent, LocalDateTime cacheTime) {
        Objects.requireNonNull(calendarEvent, "calendarEvent must not be null");
        Objects.requireNonNull(cacheTime, "cacheTime must not be null");

        return KEY_PREFIX
                + calendarEvent.eventId()
                + ":"
                + TIME_FORMATTER.format(cacheTime)
                + ":"
                + TIME_FORMATTER.format(calendarEvent.startAt())
                + ":"
                + TIME_FORMATTER.format(calendarEvent.endAt())
                + ":"
                + normalizeLocation(calendarEvent.rawLocation());
    }

    private String normalizeLocation(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return "none";
        }
        return rawLocation.trim().replaceAll("\\s+", "_");
    }
}
