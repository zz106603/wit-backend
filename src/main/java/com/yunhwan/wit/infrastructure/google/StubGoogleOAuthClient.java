package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.application.google.GoogleOAuthToken;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class StubGoogleOAuthClient implements GoogleOAuthClient {

    private final GoogleOAuthProperties properties;
    private final Clock clock;

    public StubGoogleOAuthClient(GoogleOAuthProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String buildLoginUrl() {
        return properties.authorizationBaseUrl()
                + "?response_type=code"
                + "&client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + encode(properties.scope())
                + "&state=" + encode(properties.state());
    }

    @Override
    public GoogleOAuthToken exchangeCode(String code) {
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }

        String normalizedCode = code.trim();
        return new GoogleOAuthToken(
                "stub-user@wit.local",
                "stub-access-token-" + normalizedCode,
                "stub-refresh-token-" + normalizedCode,
                LocalDateTime.now(clock).plus(properties.accessTokenExpiry())
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
