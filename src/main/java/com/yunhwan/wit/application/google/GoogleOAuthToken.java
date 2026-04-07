package com.yunhwan.wit.application.google;

import java.time.LocalDateTime;
import java.util.Objects;

public record GoogleOAuthToken(
        String email,
        String accessToken,
        String refreshToken,
        LocalDateTime accessTokenExpiresAt
) {

    public GoogleOAuthToken {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
    }
}
