package com.yunhwan.wit.infrastructure.ai;

import java.util.List;

public record GeminiGenerateContentRequest(
        List<Content> contents,
        GenerationConfig generationConfig
) {

    public record Content(
            String role,
            List<Part> parts
    ) {
    }

    public record Part(
            String text
    ) {
    }

    public record GenerationConfig(
            String responseMimeType
    ) {
    }
}
