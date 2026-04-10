package com.yunhwan.wit.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.ai.gemini")
public record GeminiApiProperties(
        String apiKey,
        String baseUrl,
        String model,
        String generateContentPath,
        Duration connectTimeout,
        Duration readTimeout
) {
}
