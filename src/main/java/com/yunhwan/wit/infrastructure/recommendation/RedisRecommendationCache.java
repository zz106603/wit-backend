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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRecommendationCache implements RecommendationCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRecommendationCache.class);
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
        String key = toKey(calendarEvent, cacheTime);
        log.info("[RecommendationDebug] Redis recommendation find before. key={}", key);
        Optional<RecommendationResult> result = find(key);
        log.info("[RecommendationDebug] Redis recommendation find after. key={}, hit={}", key, result.isPresent());
        return result;
    }

    @Override
    public void put(CalendarEvent calendarEvent, LocalDateTime cacheTime, RecommendationResult recommendationResult) {
        String key = toKey(calendarEvent, cacheTime);
        try {
            log.info("[RecommendationDebug] Redis recommendation put before. key={}", key);
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(recommendationResult),
                    properties.ttl()
            );
            log.info("[RecommendationDebug] Redis recommendation put after. key={}", key);
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
        log.info(
                "[RecommendationDebug] Redis recommendation key generation start. eventId={}, cacheTime={}",
                calendarEvent.eventId(),
                cacheTime
        );

        String key = KEY_PREFIX
                + calendarEvent.eventId()
                + ":"
                + TIME_FORMATTER.format(cacheTime)
                + ":"
                + TIME_FORMATTER.format(calendarEvent.startAt())
                + ":"
                + TIME_FORMATTER.format(calendarEvent.endAt())
                + ":"
                + normalizeLocation(calendarEvent.rawLocation());
        log.info("[RecommendationDebug] Redis recommendation key generation end. key={}", key);
        return key;
    }

    private String normalizeLocation(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return "none";
        }
        return rawLocation.trim().replaceAll("\\s+", "_");
    }
}
