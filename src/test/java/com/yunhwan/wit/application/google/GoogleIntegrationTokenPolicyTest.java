package com.yunhwan.wit.application.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GoogleIntegrationTokenPolicyTest {

    @Test
    void 만료되지_않은_access_token은_active다() {
        GoogleIntegration integration = integration(
                "access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 13, 10, 0)
        );

        GoogleAccessTokenStatus status = integration.accessTokenStatus(LocalDateTime.of(2026, 4, 13, 9, 0));

        assertThat(status).isEqualTo(GoogleAccessTokenStatus.ACTIVE);
    }

    @Test
    void 만료되었고_refresh_token이_있으면_refreshable이다() {
        GoogleIntegration integration = integration(
                "access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 13, 9, 0)
        );

        GoogleAccessTokenStatus status = integration.accessTokenStatus(LocalDateTime.of(2026, 4, 13, 9, 0));

        assertThat(status).isEqualTo(GoogleAccessTokenStatus.EXPIRED_REFRESHABLE);
    }

    @Test
    void 만료되었고_refresh_token이_없으면_reauth_required다() {
        GoogleIntegration integration = integration(
                "access-token",
                "",
                LocalDateTime.of(2026, 4, 13, 9, 0)
        );

        GoogleAccessTokenStatus status = integration.accessTokenStatus(LocalDateTime.of(2026, 4, 13, 9, 1));

        assertThat(status).isEqualTo(GoogleAccessTokenStatus.EXPIRED_REAUTH_REQUIRED);
    }

    private GoogleIntegration integration(
            String accessToken,
            String refreshToken,
            LocalDateTime accessTokenExpiresAt
    ) {
        return new GoogleIntegration(
                "default-user",
                "user@wit.local",
                accessToken,
                refreshToken,
                accessTokenExpiresAt,
                LocalDateTime.of(2026, 4, 13, 8, 0)
        );
    }
}
