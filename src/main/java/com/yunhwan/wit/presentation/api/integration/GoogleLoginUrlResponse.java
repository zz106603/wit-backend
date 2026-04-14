package com.yunhwan.wit.presentation.api.integration;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Google 로그인 URL 응답")
public record GoogleLoginUrlResponse(
        @Schema(description = "Google OAuth 로그인 URL", example = "https://accounts.google.com/o/oauth2/v2/auth?client_id=...")
        String loginUrl
) {
}
