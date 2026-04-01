package com.yunhwan.wit.infrastructure.weather;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WeatherApiProperties.class)
public class WeatherClientConfig {

    @Bean
    public RestClient weatherRestClient(RestClient.Builder builder, WeatherApiProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
