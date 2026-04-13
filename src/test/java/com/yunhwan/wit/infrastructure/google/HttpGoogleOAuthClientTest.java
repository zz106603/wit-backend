package com.yunhwan.wit.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;

import com.yunhwan.wit.application.google.GoogleOAuthToken;
import com.yunhwan.wit.application.google.GoogleAccessTokenRefreshResult;
import com.yunhwan.wit.application.google.GoogleReauthenticationRequiredException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpGoogleOAuthClientTest {

    private MockRestServiceServer server;
    private HttpGoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        googleOAuthClient = new HttpGoogleOAuthClient(
                builder.build(),
                properties(),
                Clock.fixed(Instant.parse("2026-04-07T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void 로그인_URL은_실제_Google_OAuth_파라미터를_포함한다() {
        String loginUrl = googleOAuthClient.buildLoginUrl();

        assertThat(loginUrl).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(loginUrl).contains("response_type=code");
        assertThat(loginUrl).contains("client_id=client-id");
        assertThat(loginUrl).contains("redirect_uri=http://localhost:8080/api/integrations/google/callback");
        assertThat(loginUrl).contains("scope=openid%20https://www.googleapis.com/auth/userinfo.email%20https://www.googleapis.com/auth/calendar.readonly");
        assertThat(loginUrl).contains("state=oauth-state");
        assertThat(loginUrl).contains("access_type=offline");
        assertThat(loginUrl).contains("prompt=consent");
    }

    @Test
    void authorization_code를_토큰과_사용자정보로_교환한다() {
        server.expect(requestTo("https://oauth2.googleapis.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("code=oauth-code")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fintegrations%2Fgoogle%2Fcallback")))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "expires_in": 3600,
                          "token_type": "Bearer",
                          "scope": "openid https://www.googleapis.com/auth/calendar.readonly"
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://www.googleapis.test/oauth2/v3/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, not(containsString("refresh-token"))))
                .andRespond(withSuccess("""
                        {
                          "email": "user@example.com",
                          "name": "Wit User"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleOAuthToken token = googleOAuthClient.exchangeCode("oauth-code", "oauth-state");

        assertThat(token.email()).isEqualTo("user@example.com");
        assertThat(token.accessToken()).isEqualTo("access-token");
        assertThat(token.refreshToken()).isEqualTo("refresh-token");
        assertThat(token.accessTokenExpiresAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 10, 0));
        server.verify();
    }

    @Test
    void refresh_token으로_access_token을_갱신한다() {
        server.expect(requestTo("https://oauth2.googleapis.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("refresh_token=refresh-token")))
                .andExpect(content().string(containsString("client_id=client-id")))
                .andExpect(content().string(containsString("client_secret=client-secret")))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "new-access-token",
                          "expires_in": 7200,
                          "token_type": "Bearer",
                          "scope": "openid https://www.googleapis.com/auth/calendar.readonly"
                        }
                        """, MediaType.APPLICATION_JSON));

        GoogleAccessTokenRefreshResult result = googleOAuthClient.refreshAccessToken("refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.accessTokenExpiresAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 11, 0));
        server.verify();
    }

    @Test
    void refresh_token_요청이_400이면_reauth_required를_던진다() {
        server.expect(requestTo("https://oauth2.googleapis.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> googleOAuthClient.refreshAccessToken("refresh-token"))
                .isInstanceOf(GoogleReauthenticationRequiredException.class)
                .hasMessage("Google refresh token is no longer valid");
    }

    @Test
    void state가_다르면_외부_호출_없이_거부한다() {
        assertThatThrownBy(() -> googleOAuthClient.exchangeCode("oauth-code", "invalid-state"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid Google OAuth state");
    }

    private GoogleOAuthProperties properties() {
        return new GoogleOAuthProperties(
                "client-id",
                "client-secret",
                "http://localhost:8080/api/integrations/google/callback",
                "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/calendar.readonly",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.test/token",
                "https://www.googleapis.test/oauth2/v3/userinfo",
                "oauth-state",
                Duration.ofHours(1)
        );
    }
}
