package com.yunhwan.wit.infrastructure.google;

import com.fasterxml.jackson.annotation.JsonProperty;

record GoogleTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("refresh_token")
        String refreshToken,
        @JsonProperty("expires_in")
        Long expiresIn,
        @JsonProperty("token_type")
        String tokenType,
        String scope
) {
}
