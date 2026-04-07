package com.yunhwan.wit.infrastructure.google;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.google.oauth")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String authorizationBaseUrl,
        String tokenUrl,
        String userInfoUrl,
        String state,
        Duration accessTokenExpiry
) {
}
