package com.yunhwan.wit.infrastructure.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.application.recommendation.RecommendationWeatherSource;
import com.yunhwan.wit.domain.model.CalendarEvent;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRecommendationCacheTest {

    @Test
    void 추천결과를_문서TTL로_저장한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        RedisRecommendationCache recommendationCache = new RedisRecommendationCache(
                redisTemplate,
                objectMapper,
                new RecommendationCacheProperties(Duration.ofMinutes(30))
        );

        recommendationCache.put(calendarEvent(), cacheTime(), recommendationResult());

        verify(valueOperations).set(
                eq("recommendation:event-1:2026-04-02T09:00:00:2026-04-02T18:00:00:2026-04-02T21:00:00:강남_회식"),
                eq(objectMapper.writeValueAsString(recommendationResult())),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void 추천캐시_조회키는_event_time_location으로_구성한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String serialized = objectMapper.writeValueAsString(recommendationResult());
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommendation:event-1:2026-04-02T09:00:00:2026-04-02T18:00:00:2026-04-02T21:00:00:강남_회식"))
                .thenReturn(serialized);

        RedisRecommendationCache recommendationCache = new RedisRecommendationCache(
                redisTemplate,
                objectMapper,
                new RecommendationCacheProperties(Duration.ofMinutes(30))
        );

        RecommendationResult result = recommendationCache.find(calendarEvent(), cacheTime()).orElseThrow();

        assertThat(result).isEqualTo(recommendationResult());
    }

    private CalendarEvent calendarEvent() {
        return new CalendarEvent(
                "event-1",
                "강남 회식",
                LocalDateTime.of(2026, 4, 2, 18, 0),
                LocalDateTime.of(2026, 4, 2, 21, 0),
                "강남 회식"
        );
    }

    private LocalDateTime cacheTime() {
        return LocalDateTime.of(2026, 4, 2, 9, 0);
    }

    private RecommendationResult recommendationResult() {
        return new RecommendationResult(
                new OutfitDecision(
                        true,
                        RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER,
                        "긴팔 + 가벼운 겉옷",
                        "종료 시점 비 예보가 있어 우산이 필요합니다.",
                        "도착 시점 체감온도가 현재보다 4도 이상 낮아 한 단계 더 따뜻하게 추천합니다.",
                        -7,
                        "현재보다 종료 시점이 더 쌀쌀합니다.",
                        "우산을 챙기고 긴팔 + 가벼운 겉옷 차림을 추천합니다."
                ),
                calendarEvent(),
                ResolvedLocation.resolved(
                        "강남 회식",
                        "강남",
                        "서울특별시 강남구",
                        37.5172,
                        127.0473,
                        1.0,
                        LocationResolvedBy.RULE
                ),
                null,
                new WeatherSnapshot(
                        "서울특별시 강남구",
                        LocalDateTime.of(2026, 4, 2, 9, 0),
                        24,
                        24,
                        10,
                        WeatherType.CLEAR
                ),
                new WeatherSnapshot(
                        "서울특별시 강남구",
                        LocalDateTime.of(2026, 4, 2, 18, 0),
                        21,
                        21,
                        20,
                        WeatherType.CLOUDY
                ),
                new WeatherSnapshot(
                        "서울특별시 강남구",
                        LocalDateTime.of(2026, 4, 2, 21, 0),
                        18,
                        18,
                        70,
                        WeatherType.RAIN
                ),
                false
                ,
                false,
                RecommendationWeatherSource.NORMAL
        );
    }
}
