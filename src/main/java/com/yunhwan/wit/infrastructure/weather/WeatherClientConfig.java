package com.yunhwan.wit.infrastructure.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.weather.CachingWeatherClient;
import com.yunhwan.wit.application.weather.WeatherCache;
import com.yunhwan.wit.application.weather.WeatherClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({WeatherApiProperties.class, WeatherCacheProperties.class})
public class WeatherClientConfig {

    @Bean
    public RestClient weatherRestClient(RestClient.Builder builder, WeatherApiProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    public WeatherCache weatherCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WeatherCacheProperties properties
    ) {
        return new RedisWeatherCache(redisTemplate, objectMapper, properties);
    }

    @Bean
    @Primary
    public WeatherClient weatherClient(
            HttpWeatherClient httpWeatherClient,
            WeatherCache weatherCache,
            Clock clock
    ) {
        return new CachingWeatherClient(httpWeatherClient, weatherCache, clock);
    }
}
