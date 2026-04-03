package com.yunhwan.wit.infrastructure.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.location.LocationResolutionCache;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisLocationResolutionCache implements LocationResolutionCache {

    private static final String KEY_PREFIX = "location:resolution:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LocationCacheProperties properties;

    public RedisLocationResolutionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LocationCacheProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Optional<ResolvedLocation> find(String rawLocation) {
        String key = toKey(rawLocation);
        if (key == null) {
            return Optional.empty();
        }

        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, ResolvedLocation.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize cached location resolution", exception);
        }
    }

    @Override
    public void put(String rawLocation, ResolvedLocation resolvedLocation) {
        String key = toKey(rawLocation);
        if (key == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(resolvedLocation),
                    ttlFor(resolvedLocation.status())
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize location resolution for cache", exception);
        }
    }

    private String toKey(String rawLocation) {
        if (rawLocation == null) {
            return null;
        }

        String normalized = rawLocation.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        return KEY_PREFIX + normalized;
    }

    private Duration ttlFor(LocationResolutionStatus status) {
        return properties.ttl();
    }
}
