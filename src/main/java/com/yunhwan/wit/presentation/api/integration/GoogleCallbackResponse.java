package com.yunhwan.wit.presentation.api.integration;

public record GoogleCallbackResponse(
        boolean connected,
        String email,
        int calendarEventCount
) {
}
