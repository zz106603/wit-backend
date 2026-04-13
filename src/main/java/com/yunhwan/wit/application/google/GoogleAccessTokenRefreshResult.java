package com.yunhwan.wit.application.google;

import java.time.LocalDateTime;
import java.util.Objects;

public record GoogleAccessTokenRefreshResult(
        String accessToken,
        LocalDateTime accessTokenExpiresAt
) {

    public GoogleAccessTokenRefreshResult {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
    }
}
