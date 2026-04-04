package com.yunhwan.wit.application.google;

import java.time.LocalDateTime;
import java.util.Objects;

public record GoogleIntegration(
        String userId,
        String email,
        String accessToken,
        String refreshToken,
        LocalDateTime accessTokenExpiresAt,
        LocalDateTime connectedAt
) {

    public GoogleIntegration {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
        Objects.requireNonNull(connectedAt, "connectedAt must not be null");
    }
}
