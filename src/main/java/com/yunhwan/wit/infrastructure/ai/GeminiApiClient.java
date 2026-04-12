package com.yunhwan.wit.infrastructure.ai;

public interface GeminiApiClient {

    GeminiGenerateContentResponse generateContent(String model, GeminiGenerateContentRequest request);
}
