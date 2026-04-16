package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.application.google.GoogleAccessTokenRefreshResult;
import com.yunhwan.wit.application.google.GoogleOAuthToken;
import com.yunhwan.wit.application.google.GoogleReauthenticationRequiredException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

public class HttpGoogleOAuthClient implements GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGoogleOAuthClient.class);

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";
    private static final String REFRESH_TOKEN_GRANT_TYPE = "refresh_token";

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
            log.warn("Google token response is invalid");
            throw new GoogleIntegrationInfrastructureException("Google token response is invalid");
        }

        GoogleUserInfoResponse userInfoResponse = requestUserInfo(tokenResponse.accessToken());
        if (userInfoResponse == null || !StringUtils.hasText(userInfoResponse.email())) {
            log.warn("Google user info response is invalid");
            throw new GoogleIntegrationInfrastructureException("Google user info response is invalid");
        }

        return new GoogleOAuthToken(
                userInfoResponse.email(),
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                LocalDateTime.now(clock).plusSeconds(resolveExpiresIn(tokenResponse))
        );
    }

    @Override
    public GoogleAccessTokenRefreshResult refreshAccessToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }

        GoogleTokenResponse tokenResponse = requestRefreshToken(refreshToken);
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken())) {
            log.warn("Google refresh token response is invalid");
            throw new GoogleIntegrationInfrastructureException("Google refresh token response is invalid");
        }

        return new GoogleAccessTokenRefreshResult(
                tokenResponse.accessToken(),
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
            log.warn("Google token request failed. status={}", exception.getStatusCode().value(), exception);
            throw new GoogleIntegrationInfrastructureException("Google token request failed", exception);
        } catch (RestClientException exception) {
            log.warn("Google token communication failed", exception);
            throw new GoogleIntegrationInfrastructureException("Google token communication failed", exception);
        }
    }

    private GoogleTokenResponse requestRefreshToken(String refreshToken) {
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("refresh_token", refreshToken.trim());
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            form.add("grant_type", REFRESH_TOKEN_GRANT_TYPE);

            return googleOAuthRestClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientResponseException exception) {
            if (isReauthenticationRequired(exception.getStatusCode())) {
                throw new GoogleReauthenticationRequiredException("Google refresh token is no longer valid");
            }
            log.warn("Google refresh token request failed. status={}", exception.getStatusCode().value(), exception);
            throw new GoogleIntegrationInfrastructureException("Google refresh token request failed", exception);
        } catch (RestClientException exception) {
            log.warn("Google refresh token communication failed", exception);
            throw new GoogleIntegrationInfrastructureException("Google refresh token communication failed", exception);
        }
    }

    private boolean isReauthenticationRequired(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 400 || value == 401;
    }

    private GoogleUserInfoResponse requestUserInfo(String accessToken) {
        try {
            return googleOAuthRestClient.get()
                    .uri(properties.userInfoUrl())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Google user info request failed. status={}", exception.getStatusCode().value(), exception);
            throw new GoogleIntegrationInfrastructureException("Google user info request failed", exception);
        } catch (RestClientException exception) {
            log.warn("Google user info communication failed", exception);
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
