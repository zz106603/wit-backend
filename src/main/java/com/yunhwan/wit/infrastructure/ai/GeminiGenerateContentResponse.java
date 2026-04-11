package com.yunhwan.wit.infrastructure.ai;

import java.util.List;

public record GeminiGenerateContentResponse(
        List<Candidate> candidates
) {

    public record Candidate(
            Content content
    ) {
    }

    public record Content(
            List<Part> parts
    ) {
    }

    public record Part(
            String text
    ) {
    }
}
