package com.yunhwan.wit.infrastructure.ai;

import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpGeminiApiClient implements GeminiApiClient {

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
                throw new GeminiInfrastructureException("Gemini API returned empty response");
            }

            return response;
        } catch (RestClientResponseException exception) {
            throw new GeminiInfrastructureException("Gemini API request failed", exception);
        } catch (RestClientException exception) {
            throw new GeminiInfrastructureException("Gemini API communication failed", exception);
        }
    }
}
