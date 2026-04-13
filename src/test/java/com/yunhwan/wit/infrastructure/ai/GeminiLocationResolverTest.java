package com.yunhwan.wit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.location.DefaultLocationResolver;
import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.application.location.RuleBasedLocationResolver;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiLocationResolverTest {

    private final GeminiApiClient geminiApiClient = mock(GeminiApiClient.class);
    private final GeminiApiProperties properties = new GeminiApiProperties(
            "test-key",
            "https://example.com",
            "gemini-test",
            "/generate",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)
    );
    private final GeminiLocationResolver resolver = new GeminiLocationResolver(
            geminiApiClient,
            properties,
            new ObjectMapper()
    );

    @Test
    void gemini_api_예외가_발생하면_failed를_반환하고_예외를_던지지_않는다() {
        when(geminiApiClient.generateContent(eq("gemini-test"), any(GeminiGenerateContentRequest.class)))
                .thenThrow(new GeminiInfrastructureException("Gemini API failed"));

        ResolvedLocation result = resolver.resolve("회사 회식");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("회사 회식");
        assertThat(result.resolvedBy()).isNull();
        verify(geminiApiClient).generateContent(eq("gemini-test"), any(GeminiGenerateContentRequest.class));
    }

    @Test
    void gemini_json_파싱에_실패하면_failed를_반환한다() {
        when(geminiApiClient.generateContent(eq("gemini-test"), any(GeminiGenerateContentRequest.class)))
                .thenReturn(response("not-json"));

        ResolvedLocation result = resolver.resolve("회사 회식");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("회사 회식");
        assertThat(result.resolvedBy()).isNull();
    }

    @Test
    void default_location_resolver는_gemini_예외시에도_최종_failed_흐름을_계속_진행한다() {
        GooglePlacesLocationResolver googlePlacesResolver = rawLocation -> ResolvedLocation.failed(rawLocation);
        RuleBasedLocationResolver ruleBasedLocationResolver = new RuleBasedLocationResolver();
        DefaultLocationResolver defaultLocationResolver = new DefaultLocationResolver(
                ruleBasedLocationResolver,
                googlePlacesResolver,
                resolver
        );
        when(geminiApiClient.generateContent(eq("gemini-test"), any(GeminiGenerateContentRequest.class)))
                .thenThrow(new GeminiInfrastructureException("Gemini API failed"));

        ResolvedLocation result = defaultLocationResolver.resolve("회사 회식");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("회사 회식");
        assertThat(result.resolvedBy()).isNull();
    }

    private GeminiGenerateContentResponse response(String text) {
        return new GeminiGenerateContentResponse(List.of(
                new GeminiGenerateContentResponse.Candidate(
                        new GeminiGenerateContentResponse.Content(
                                List.of(new GeminiGenerateContentResponse.Part(text))
                        )
                )
        ));
    }
}
