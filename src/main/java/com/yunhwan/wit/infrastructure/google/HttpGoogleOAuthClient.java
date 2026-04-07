package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.application.google.GoogleOAuthToken;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class HttpGoogleOAuthClient implements GoogleOAuthClient {

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";

    private final RestClient googleOAuthRestClient;
    private final GoogleOAuthProperties properties;
    private final Clock clock;

    public HttpGoogleOAuthClient(
            RestClient googleOAuthRestClient,
            GoogleOAuthProperties properties,
            Clock clock
    ) {
        this.googleOAuthRestClient = Objects.requireNonNull(
                googleOAuthRestClient,
                "googleOAuthRestClient must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String buildLoginUrl() {
        return UriComponentsBuilder.fromUriString(properties.authorizationBaseUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scope())
                .queryParam("state", properties.state())
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    @Override
    public GoogleOAuthToken exchangeCode(String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (!Objects.equals(properties.state(), state)) {
            throw new IllegalArgumentException("invalid Google OAuth state");
        }

        GoogleTokenResponse tokenResponse = requestToken(code);
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken())) {
            throw new GoogleIntegrationInfrastructureException("Google token response is invalid");
        }

        GoogleUserInfoResponse userInfoResponse = requestUserInfo(tokenResponse.accessToken());
        if (userInfoResponse == null || !StringUtils.hasText(userInfoResponse.email())) {
            throw new GoogleIntegrationInfrastructureException("Google user info response is invalid");
        }

        return new GoogleOAuthToken(
                userInfoResponse.email(),
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                LocalDateTime.now(clock).plusSeconds(resolveExpiresIn(tokenResponse))
        );
    }

    private GoogleTokenResponse requestToken(String code) {
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code.trim());
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            form.add("redirect_uri", properties.redirectUri());
            form.add("grant_type", AUTHORIZATION_CODE_GRANT_TYPE);

            return googleOAuthRestClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw new GoogleIntegrationInfrastructureException("Google token request failed", exception);
        } catch (RestClientException exception) {
            throw new GoogleIntegrationInfrastructureException("Google token communication failed", exception);
        }
    }

    private GoogleUserInfoResponse requestUserInfo(String accessToken) {
        try {
            return googleOAuthRestClient.get()
                    .uri(properties.userInfoUrl())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
        } catch (RestClientResponseException exception) {
            throw new GoogleIntegrationInfrastructureException("Google user info request failed", exception);
        } catch (RestClientException exception) {
            throw new GoogleIntegrationInfrastructureException("Google user info communication failed", exception);
        }
    }

    private long resolveExpiresIn(GoogleTokenResponse tokenResponse) {
        if (tokenResponse.expiresIn() == null || tokenResponse.expiresIn() <= 0) {
            return properties.accessTokenExpiry().toSeconds();
        }
        return tokenResponse.expiresIn();
    }
}
