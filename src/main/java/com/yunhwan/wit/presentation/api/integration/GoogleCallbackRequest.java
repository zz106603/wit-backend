package com.yunhwan.wit.presentation.api.integration;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google OAuth callback 요청")
public record GoogleCallbackRequest(
        @NotBlank
        @Schema(description = "Google OAuth authorization code", example = "4/0AdQt8q...")
        String code,
        @NotBlank
        @Schema(description = "OAuth state", example = "wit-google-state")
        String state
) {
}
