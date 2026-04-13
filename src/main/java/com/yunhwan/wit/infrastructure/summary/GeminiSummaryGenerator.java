package com.yunhwan.wit.infrastructure.summary;

import com.yunhwan.wit.application.summary.SummaryGenerationInput;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.infrastructure.ai.GeminiApiClient;
import com.yunhwan.wit.infrastructure.ai.GeminiApiProperties;
import com.yunhwan.wit.infrastructure.ai.GeminiGenerateContentRequest;
import com.yunhwan.wit.infrastructure.ai.GeminiGenerateContentResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class GeminiSummaryGenerator implements SummaryGenerator {

    private static final String RESPONSE_MIME_TYPE = "text/plain";

    private final GeminiApiClient geminiApiClient;
    private final GeminiApiProperties properties;

    public GeminiSummaryGenerator(
            GeminiApiClient geminiApiClient,
            GeminiApiProperties properties
    ) {
        this.geminiApiClient = Objects.requireNonNull(geminiApiClient, "geminiApiClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String generate(SummaryGenerationInput input) {
        Objects.requireNonNull(input, "input must not be null");

        GeminiGenerateContentResponse response = geminiApiClient.generateContent(
                properties.model(),
                buildRequest(input)
        );

        String summary = extractText(response);
        if (!StringUtils.hasText(summary)) {
            throw new IllegalStateException("Gemini summary response was blank");
        }

        return summary.trim();
    }

    private GeminiGenerateContentRequest buildRequest(SummaryGenerationInput input) {
        OutfitDecision decision = input.outfitDecision();
        String prompt = """
                아래는 이미 규칙 엔진이 계산한 최종 추천 결과다.
                너는 판단을 바꾸지 말고, 주어진 정보를 짧고 실용적인 한국어 1~2문장으로만 요약하라.
                과장하지 말고, 우산 여부와 옷차림을 자연스럽게 설명하라.
                규칙/판단을 새로 만들지 마라.

                현재 날씨:
                %s

                시작 시점 날씨:
                %s

                종료 시점 날씨:
                %s

                최종 추천 결과:
                - needUmbrella: %s
                - recommendedOutfitText: %s
                - umbrellaReason: %s
                - outfitReason: %s
                - weatherChangeSummary: %s
                """.formatted(
                weatherText(input.currentWeather()),
                weatherText(input.startWeather()),
                weatherText(input.endWeather()),
                decision.needUmbrella(),
                decision.recommendedOutfitText(),
                nullableText(decision.umbrellaReason()),
                nullableText(decision.outfitReason()),
                nullableText(decision.weatherChangeSummary())
        );

        return new GeminiGenerateContentRequest(
                List.of(new GeminiGenerateContentRequest.Content(
                        "user",
                        List.of(new GeminiGenerateContentRequest.Part(prompt))
                )),
                new GeminiGenerateContentRequest.GenerationConfig(RESPONSE_MIME_TYPE)
        );
    }

    private String extractText(GeminiGenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
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

    private String weatherText(WeatherSnapshot weatherSnapshot) {
        if (weatherSnapshot == null) {
            return "없음";
        }

        return "region=%s, targetTime=%s, temperature=%d, feelsLike=%d, precipitationProbability=%d, weatherType=%s"
                .formatted(
                        weatherSnapshot.regionName(),
                        weatherSnapshot.targetTime(),
                        weatherSnapshot.temperature(),
                        weatherSnapshot.feelsLike(),
                        weatherSnapshot.precipitationProbability(),
                        weatherSnapshot.weatherType()
                );
    }

    private String nullableText(String text) {
        return StringUtils.hasText(text) ? text : "없음";
    }
}
