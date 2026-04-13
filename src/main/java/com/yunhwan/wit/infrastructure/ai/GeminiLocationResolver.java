package com.yunhwan.wit.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.location.AiLocationFallbackResolver;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class GeminiLocationResolver implements AiLocationFallbackResolver {

    private static final Logger log = LoggerFactory.getLogger(GeminiLocationResolver.class);
    private static final String LOG_PREFIX = "[LocationResolverFlow]";
    private static final String RESPONSE_MIME_TYPE = "application/json";
    private static final double DEFAULT_AI_CONFIDENCE = 0.7;

    private final GeminiApiClient geminiApiClient;
    private final GeminiApiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiLocationResolver(
            GeminiApiClient geminiApiClient,
            GeminiApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.geminiApiClient = Objects.requireNonNull(geminiApiClient, "geminiApiClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public ResolvedLocation resolve(String rawLocation) {
        if (!StringUtils.hasText(rawLocation)) {
            log.info("{} rawLocation={}, step=AI, status=FAILED, reason=blank-input", LOG_PREFIX, rawLocation);
            return ResolvedLocation.failed(rawLocation);
        }

        try {
            log.info("{} rawLocation={}, step=AI, action=REQUEST", LOG_PREFIX, rawLocation);
            GeminiGenerateContentResponse response = geminiApiClient.generateContent(
                    properties.model(),
                    buildRequest(rawLocation)
            );

            String text = extractText(response);
            if (!StringUtils.hasText(text)) {
                log.info("{} rawLocation={}, step=AI, status=FAILED, reason=empty-response-text", LOG_PREFIX, rawLocation);
                return ResolvedLocation.failed(rawLocation);
            }

            GeminiLocationPayload payload = parsePayload(text);
            if (payload == null || !isUsablePayload(payload)) {
                log.info("{} rawLocation={}, step=AI, status=FAILED, reason=unusable-payload", LOG_PREFIX, rawLocation);
                return ResolvedLocation.failed(rawLocation);
            }

            LocationResolutionStatus status = resolveStatus(payload.status());
            if (status == LocationResolutionStatus.FAILED) {
                log.info("{} rawLocation={}, step=AI, status=FAILED, reason=model-returned-failed", LOG_PREFIX, rawLocation);
                return ResolvedLocation.failed(rawLocation);
            }

            double confidence = resolveConfidence(payload.confidence());
            if (status == LocationResolutionStatus.RESOLVED) {
                log.info(
                        "{} rawLocation={}, step=AI, status={}, result=RESOLVED, displayLocation={}",
                        LOG_PREFIX,
                        rawLocation,
                        status,
                        payload.displayLocation().trim()
                );
                return ResolvedLocation.resolved(
                        rawLocation,
                        payload.normalizedQuery().trim(),
                        payload.displayLocation().trim(),
                        payload.lat(),
                        payload.lng(),
                        confidence,
                        LocationResolvedBy.AI
                );
            }

            log.info(
                    "{} rawLocation={}, step=AI, status={}, result=APPROXIMATED, displayLocation={}",
                    LOG_PREFIX,
                    rawLocation,
                    status,
                    payload.displayLocation().trim()
            );
            return ResolvedLocation.approximated(
                    rawLocation,
                    payload.normalizedQuery().trim(),
                    payload.displayLocation().trim(),
                    payload.lat(),
                    payload.lng(),
                    confidence,
                    LocationResolvedBy.AI
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "{} rawLocation={}, step=AI, status=FAILED, reason=exception-caught",
                    LOG_PREFIX,
                    rawLocation,
                    exception
            );
            log.info(
                    "{} rawLocation={}, step=AI, status=FAILED, result=RETURNED, reason=exception-caught",
                    LOG_PREFIX,
                    rawLocation
            );
            return ResolvedLocation.failed(rawLocation);
        }
    }

    private GeminiGenerateContentRequest buildRequest(String rawLocation) {
        String prompt = """
                다음 입력에서 외출 장소를 추론해 한국 기준 위치 정보를 JSON으로만 반환하라.
                추론이 가능하면 status는 RESOLVED 또는 APPROXIMATED 중 하나로 하라.
                추론이 불가능하면 status는 FAILED로 하라.
                JSON schema:
                {
                  "normalizedQuery": "string or null",
                  "displayLocation": "string or null",
                  "lat": number or null,
                  "lng": number or null,
                  "confidence": number between 0 and 1 or null,
                  "status": "RESOLVED" | "APPROXIMATED" | "FAILED"
                }
                입력: "%s"
                """.formatted(rawLocation);

        return new GeminiGenerateContentRequest(
                List.of(new GeminiGenerateContentRequest.Content(
                        "user",
                        List.of(new GeminiGenerateContentRequest.Part(prompt))
                )),
                new GeminiGenerateContentRequest.GenerationConfig(RESPONSE_MIME_TYPE)
        );
    }

    private String extractText(GeminiGenerateContentResponse response) {
        if (response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }

        GeminiGenerateContentResponse.Candidate candidate = response.candidates().getFirst();
        if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
            return null;
        }

        return candidate.content().parts().stream()
                .map(GeminiGenerateContentResponse.Part::text)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private GeminiLocationPayload parsePayload(String text) {
        try {
            return objectMapper.readValue(text, GeminiLocationPayload.class);
        } catch (JsonProcessingException exception) {
            throw new GeminiInfrastructureException("Gemini response parsing failed", exception);
        }
    }

    private boolean isUsablePayload(GeminiLocationPayload payload) {
        if (!StringUtils.hasText(payload.normalizedQuery())) {
            return false;
        }
        if (!StringUtils.hasText(payload.displayLocation())) {
            return false;
        }
        return payload.lat() != null && payload.lng() != null;
    }

    private LocationResolutionStatus resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return LocationResolutionStatus.FAILED;
        }

        return switch (status.trim().toUpperCase()) {
            case "RESOLVED" -> LocationResolutionStatus.RESOLVED;
            case "APPROXIMATED" -> LocationResolutionStatus.APPROXIMATED;
            default -> LocationResolutionStatus.FAILED;
        };
    }

    private double resolveConfidence(Double confidence) {
        if (confidence == null) {
            return DEFAULT_AI_CONFIDENCE;
        }
        if (confidence < 0.0) {
            return 0.0;
        }
        if (confidence > 1.0) {
            return 1.0;
        }
        return confidence;
    }

    private record GeminiLocationPayload(
            String normalizedQuery,
            String displayLocation,
            Double lat,
            Double lng,
            Double confidence,
            String status
    ) {
    }
}
