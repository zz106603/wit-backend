package com.yunhwan.wit.infrastructure.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisLocationResolutionCacheTest {

    @Test
    void location결과를_기본TTL로_저장한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        RedisLocationResolutionCache cache = new RedisLocationResolutionCache(
                redisTemplate,
                objectMapper,
                new LocationCacheProperties(Duration.ofHours(24))
        );

        cache.put("강남 회식", resolvedLocation());

        verify(valueOperations).set(
                eq("location:resolution:강남 회식"),
                eq(objectMapper.writeValueAsString(resolvedLocation())),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void location조회키는_trim과_lower_case로_정규화한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String serialized = objectMapper.writeValueAsString(resolvedLocation());
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("location:resolution:gangnam station")).thenReturn(serialized);

        RedisLocationResolutionCache cache = new RedisLocationResolutionCache(
                redisTemplate,
                objectMapper,
                new LocationCacheProperties(Duration.ofHours(24))
        );

        ResolvedLocation result = cache.find("  Gangnam Station  ").orElseThrow();

        assertThat(result).isEqualTo(resolvedLocation());
    }

    private ResolvedLocation resolvedLocation() {
        return ResolvedLocation.resolved(
                "Gangnam Station",
                "gangnam station",
                "서울특별시 강남구",
                37.4979,
                127.0276,
                0.9,
                LocationResolvedBy.GOOGLE_PLACES
        );
    }
}
