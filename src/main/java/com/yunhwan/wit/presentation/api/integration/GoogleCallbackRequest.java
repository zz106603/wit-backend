package com.yunhwan.wit.presentation.api.integration;

import jakarta.validation.constraints.NotBlank;

public record GoogleCallbackRequest(
        @NotBlank(message = "code는 필수입니다.")
        String code
) {
}
