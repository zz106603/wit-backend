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
                Final recommendation is already decided by the rule engine.
                Do not change any decision and do not add new reasoning.
                Output only 1-2 short, practical Korean sentences.
                Be date-aware:
                - Use "오늘" only if the event date matches the reference date.
                - If the event is the next day, do not say "오늘"; use "내일" or neutral wording.
                - If the event is later, use a date expression or neutral wording.
                - Do not generate or guess weekday names.
                - If exact weekday is not explicitly provided, do not mention it.
                Keep the summary recommendation-focused.
                Do not mention fallback, failure, cache, approximation, or system behavior.
                Always include both umbrella guidance and outfit guidance.
                Do not omit either one, even if one seems less important.
                Include numeric weather values only when they materially improve clarity.
                If rain risk is meaningful, you may include precipitation probability once.
                If temperature directly helps explain the outfit, you may include one temperature value once.
                Do not include unnecessary numbers.
                Mention them naturally and keep the summary concise.

                Reference time:
                %s

                Current weather:
                %s

                Start weather:
                %s

                End weather:
                %s

                Final recommendation:
                - needUmbrella: %s
                - recommendedOutfitText: %s
                - umbrellaReason: %s
                - outfitReason: %s
                - weatherChangeSummary: %s
                """.formatted(
                input.referenceTime(),
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
