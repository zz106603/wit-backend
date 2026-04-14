package com.yunhwan.wit.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisWeatherCacheTest {

    @Test
    void 현재날씨를_문서TTL로_저장한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("weather:latest:current:37.4979:127.0276")).thenReturn(null);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        RedisWeatherCache weatherCache = new RedisWeatherCache(
                redisTemplate,
                objectMapper,
                new WeatherCacheProperties(Duration.ofHours(1))
        );

        weatherCache.putCurrent(resolvedLocation(), LocalDateTime.of(2026, 4, 1, 9, 0), snapshot());

        verify(valueOperations).set(
                eq("weather:current:37.4979:127.0276:2026-04-01T09:00:00"),
                eq(objectMapper.writeValueAsString(snapshot())),
                eq(Duration.ofHours(1))
        );
        verify(valueOperations).set(
                eq("weather:latest:current:37.4979:127.0276"),
                eq(objectMapper.writeValueAsString(snapshot())),
                eq(Duration.ofHours(1))
        );
    }

    @Test
    void 예보캐시_조회키는_좌표와_요청시각으로_구성한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String serialized = objectMapper.writeValueAsString(snapshot());
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("weather:forecast:37.4979:127.0276:2026-04-01T18:00:00")).thenReturn(serialized);

        RedisWeatherCache weatherCache = new RedisWeatherCache(
                redisTemplate,
                objectMapper,
                new WeatherCacheProperties(Duration.ofHours(1))
        );

        WeatherSnapshot result = weatherCache.findForecast(
                resolvedLocation(),
                LocalDateTime.of(2026, 4, 1, 18, 0)
        ).orElseThrow();

        assertThat(result).isEqualTo(snapshot());
    }

    @Test
    void latest_forecast는_고정_latest_key로_조회한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String serialized = objectMapper.writeValueAsString(snapshot());
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("weather:latest:forecast:37.4979:127.0276")).thenReturn(serialized);

        RedisWeatherCache weatherCache = new RedisWeatherCache(
                redisTemplate,
                objectMapper,
                new WeatherCacheProperties(Duration.ofHours(1))
        );

        WeatherSnapshot result = weatherCache.findLatestForecast(resolvedLocation()).orElseThrow();

        assertThat(result).isEqualTo(snapshot());
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

    private WeatherSnapshot snapshot() {
        return new WeatherSnapshot(
                "서울특별시 강남구",
                LocalDateTime.of(2026, 4, 1, 18, 0),
                16,
                14,
                70,
                WeatherType.RAIN
        );
    }
}
