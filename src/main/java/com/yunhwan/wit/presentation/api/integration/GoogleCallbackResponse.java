package com.yunhwan.wit.presentation.api.integration;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Google 연동 완료 응답")
public record GoogleCallbackResponse(
        @Schema(description = "연동 성공 여부", example = "true")
        boolean connected,
        @Schema(description = "연동된 Google 계정 이메일", example = "user@wit.local")
        String email,
        @Schema(description = "조회된 캘린더 이벤트 수", example = "3")
        int calendarEventCount
) {
}
