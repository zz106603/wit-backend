package com.yunhwan.wit.infrastructure.ai;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpGeminiApiClient implements GeminiApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGeminiApiClient.class);

    private final RestClient geminiRestClient;
    private final GeminiApiProperties properties;

    public HttpGeminiApiClient(
            RestClient geminiRestClient,
            GeminiApiProperties properties
    ) {
        this.geminiRestClient = Objects.requireNonNull(geminiRestClient, "geminiRestClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public GeminiGenerateContentResponse generateContent(String model, GeminiGenerateContentRequest request) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalArgumentException("Gemini API key must not be blank");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("Gemini model must not be blank");
        }
        Objects.requireNonNull(request, "request must not be null");

        try {
            GeminiGenerateContentResponse response = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.generateContentPath())
                            .queryParam("key", properties.apiKey())
                            .build(model))
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);

            if (response == null) {
                log.warn("Gemini API returned empty response. model={}", model);
                throw new GeminiInfrastructureException("Gemini API returned empty response");
            }

            return response;
        } catch (RestClientResponseException exception) {
            log.warn("Gemini API request failed. model={}, status={}", model, exception.getStatusCode().value(), exception);
            throw new GeminiInfrastructureException("Gemini API request failed", exception);
        } catch (RestClientException exception) {
            log.warn("Gemini API communication failed. model={}", model, exception);
            throw new GeminiInfrastructureException("Gemini API communication failed", exception);
        }
    }
}
