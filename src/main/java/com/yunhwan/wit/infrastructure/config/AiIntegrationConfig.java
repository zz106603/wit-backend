package com.yunhwan.wit.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.infrastructure.ai.GeminiApiClient;
import com.yunhwan.wit.infrastructure.ai.GeminiApiProperties;
import com.yunhwan.wit.infrastructure.ai.GeminiLocationResolver;
import com.yunhwan.wit.infrastructure.ai.HttpGeminiApiClient;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiApiProperties.class)
public class AiIntegrationConfig {

    @Bean
    public RestClient geminiRestClient(RestClient.Builder builder, GeminiApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public GeminiApiClient geminiApiClient(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            GeminiApiProperties properties
    ) {
        return new HttpGeminiApiClient(geminiRestClient, properties);
    }

    @Bean
    public GeminiLocationResolver geminiLocationResolver(
            GeminiApiClient geminiApiClient,
            GeminiApiProperties properties,
            ObjectMapper objectMapper
    ) {
        return new GeminiLocationResolver(geminiApiClient, properties, objectMapper);
    }
}
