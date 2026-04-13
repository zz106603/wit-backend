package com.yunhwan.wit.application.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoogleIntegrationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-04T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 로그인_URL을_반환한다() {
        GoogleIntegrationService service = new GoogleIntegrationService(
                new FixedLoginUrlGoogleOAuthClient("https://accounts.google.com/o/oauth2/v2/auth?client_id=test"),
                new RecordingGoogleCalendarClient(List.of()),
                new InMemoryGoogleIntegrationRepository(),
                () -> "default-user",
                clock
        );

        GoogleLoginUrlResult result = service.getLoginUrl();

        assertThat(result.loginUrl()).contains("client_id=test");
    }

    @Test
    void callback_처리시_토큰을_저장하고_캘린더_이벤트를_조회한다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        List<CalendarEvent> events = List.of(
                new CalendarEvent(
                        "event-1",
                        "저녁 회식",
                        LocalDateTime.of(2026, 4, 4, 18, 0),
                        LocalDateTime.of(2026, 4, 4, 20, 0),
                        "강남"
                )
        );
        RecordingGoogleCalendarClient calendarClient = new RecordingGoogleCalendarClient(events);

        GoogleIntegrationService service = new GoogleIntegrationService(
                new StubGoogleOAuthClient(),
                calendarClient,
                repository,
                () -> "default-user",
                clock
        );

        GoogleConnectionResult result = service.connect(new GoogleCallbackCommand("oauth-code", "oauth-state"));

        assertThat(result.connected()).isTrue();
        assertThat(result.calendarEvents()).hasSize(1);
        assertThat(result.googleIntegration().email()).isEqualTo("user@wit.local");
        assertThat(repository.findByUserId("default-user")).isPresent();
        assertThat(calendarClient.lastLimit).isEqualTo(3);
    }

    @Test
    void 저장된_연동정보가_있으면_이후_캘린더_이벤트를_재조회한다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        repository.save(new GoogleIntegration(
                "default-user",
                "user@wit.local",
                "access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 4, 10, 0),
                LocalDateTime.of(2026, 4, 4, 9, 0)
        ));
        RecordingGoogleCalendarClient calendarClient = new RecordingGoogleCalendarClient(List.of(
                new CalendarEvent(
                        "event-2",
                        "점심 미팅",
                        LocalDateTime.of(2026, 4, 4, 12, 0),
                        LocalDateTime.of(2026, 4, 4, 13, 0),
                        "판교"
                )
        ));
        GoogleIntegrationService service = new GoogleIntegrationService(
                new FixedLoginUrlGoogleOAuthClient("unused"),
                calendarClient,
                repository,
                () -> "default-user",
                clock
        );

        List<CalendarEvent> result = service.getUpcomingEvents();

        assertThat(result).hasSize(1);
        assertThat(calendarClient.lastIntegration).isNotNull();
    }

    @Test
    void 만료된_토큰이_refresh가능하면_refresh후_같은요청으로_캘린더를_조회한다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        repository.save(new GoogleIntegration(
                "default-user",
                "user@wit.local",
                "expired-access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 4, 8, 0),
                LocalDateTime.of(2026, 4, 4, 7, 0)
        ));
        RecordingGoogleCalendarClient calendarClient = new RecordingGoogleCalendarClient(List.of(event("event-3")));
        RefreshableGoogleOAuthClient googleOAuthClient = RefreshableGoogleOAuthClient.success(
                "new-access-token",
                LocalDateTime.of(2026, 4, 4, 12, 0)
        );
        GoogleIntegrationService service = new GoogleIntegrationService(
                googleOAuthClient,
                calendarClient,
                repository,
                () -> "default-user",
                clock
        );

        List<CalendarEvent> result = service.getUpcomingEvents();

        assertThat(result).hasSize(1);
        assertThat(googleOAuthClient.refreshInvocationCount).isEqualTo(1);
        assertThat(calendarClient.lastIntegration.accessToken()).isEqualTo("new-access-token");
        assertThat(repository.findByUserId("default-user")).get().extracting(GoogleIntegration::accessToken)
                .isEqualTo("new-access-token");
    }

    @Test
    void 만료된_토큰_refresh중_reauth가_필요하면_예외를_던진다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        repository.save(new GoogleIntegration(
                "default-user",
                "user@wit.local",
                "expired-access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 4, 8, 0),
                LocalDateTime.of(2026, 4, 4, 7, 0)
        ));
        GoogleIntegrationService service = new GoogleIntegrationService(
                RefreshableGoogleOAuthClient.reauthRequired(),
                new RecordingGoogleCalendarClient(List.of(event("event-4"))),
                repository,
                () -> "default-user",
                clock
        );

        assertThatThrownBy(service::getUpcomingEvents)
                .isInstanceOf(GoogleReauthenticationRequiredException.class);
    }

    @Test
    void 만료되었고_refresh불가면_즉시_reauth_required를_던진다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        repository.save(new GoogleIntegration(
                "default-user",
                "user@wit.local",
                "expired-access-token",
                "",
                LocalDateTime.of(2026, 4, 4, 8, 0),
                LocalDateTime.of(2026, 4, 4, 7, 0)
        ));
        RefreshableGoogleOAuthClient googleOAuthClient = RefreshableGoogleOAuthClient.success(
                "new-access-token",
                LocalDateTime.of(2026, 4, 4, 12, 0)
        );
        GoogleIntegrationService service = new GoogleIntegrationService(
                googleOAuthClient,
                new RecordingGoogleCalendarClient(List.of(event("event-5"))),
                repository,
                () -> "default-user",
                clock
        );

        assertThatThrownBy(service::getUpcomingEvents)
                .isInstanceOf(GoogleReauthenticationRequiredException.class);
        assertThat(googleOAuthClient.refreshInvocationCount).isZero();
    }

    @Test
    void refresh외부실패면_integration_unavailable을_던진다() {
        InMemoryGoogleIntegrationRepository repository = new InMemoryGoogleIntegrationRepository();
        repository.save(new GoogleIntegration(
                "default-user",
                "user@wit.local",
                "expired-access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 4, 8, 0),
                LocalDateTime.of(2026, 4, 4, 7, 0)
        ));
        GoogleIntegrationService service = new GoogleIntegrationService(
                RefreshableGoogleOAuthClient.externalFailure(),
                new RecordingGoogleCalendarClient(List.of(event("event-6"))),
                repository,
                () -> "default-user",
                clock
        );

        assertThatThrownBy(service::getUpcomingEvents)
                .isInstanceOf(GoogleIntegrationUnavailableException.class);
    }

    private CalendarEvent event(String eventId) {
        return new CalendarEvent(
                eventId,
                "점심 미팅",
                LocalDateTime.of(2026, 4, 4, 12, 0),
                LocalDateTime.of(2026, 4, 4, 13, 0),
                "판교"
        );
    }

    private static class StubGoogleOAuthClient implements GoogleOAuthClient {

        @Override
        public String buildLoginUrl() {
            return "https://accounts.google.com/o/oauth2/v2/auth";
        }

        @Override
        public GoogleOAuthToken exchangeCode(String code, String state) {
            return new GoogleOAuthToken(
                    "user@wit.local",
                    "access-token",
                    "refresh-token",
                    LocalDateTime.of(2026, 4, 4, 10, 0)
            );
        }

        @Override
        public GoogleAccessTokenRefreshResult refreshAccessToken(String refreshToken) {
            throw new UnsupportedOperationException("refreshAccessToken is not used in this test");
        }
    }

    private static class FixedLoginUrlGoogleOAuthClient implements GoogleOAuthClient {

        private final String loginUrl;

        private FixedLoginUrlGoogleOAuthClient(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        @Override
        public String buildLoginUrl() {
            return loginUrl;
        }

        @Override
        public GoogleOAuthToken exchangeCode(String code, String state) {
            throw new UnsupportedOperationException("exchangeCode is not used in this test");
        }

        @Override
        public GoogleAccessTokenRefreshResult refreshAccessToken(String refreshToken) {
            throw new UnsupportedOperationException("refreshAccessToken is not used in this test");
        }
    }

    private static class RefreshableGoogleOAuthClient implements GoogleOAuthClient {

        private final GoogleAccessTokenRefreshResult refreshResult;
        private final RuntimeException refreshException;
        private int refreshInvocationCount;

        private RefreshableGoogleOAuthClient(
                GoogleAccessTokenRefreshResult refreshResult,
                RuntimeException refreshException
        ) {
            this.refreshResult = refreshResult;
            this.refreshException = refreshException;
        }

        private static RefreshableGoogleOAuthClient success(String accessToken, LocalDateTime expiresAt) {
            return new RefreshableGoogleOAuthClient(
                    new GoogleAccessTokenRefreshResult(accessToken, expiresAt),
                    null
            );
        }

        private static RefreshableGoogleOAuthClient reauthRequired() {
            return new RefreshableGoogleOAuthClient(
                    null,
                    new GoogleReauthenticationRequiredException("reauth required")
            );
        }

        private static RefreshableGoogleOAuthClient externalFailure() {
            return new RefreshableGoogleOAuthClient(
                    null,
                    new GoogleIntegrationUnavailableException("google refresh unavailable")
            );
        }

        @Override
        public String buildLoginUrl() {
            return "unused";
        }

        @Override
        public GoogleOAuthToken exchangeCode(String code, String state) {
            throw new UnsupportedOperationException("exchangeCode is not used in this test");
        }

        @Override
        public GoogleAccessTokenRefreshResult refreshAccessToken(String refreshToken) {
            refreshInvocationCount++;
            if (refreshException != null) {
                throw refreshException;
            }
            return refreshResult;
        }
    }

    private static class RecordingGoogleCalendarClient implements GoogleCalendarClient {

        private final List<CalendarEvent> events;
        private GoogleIntegration lastIntegration;
        private int lastLimit;

        private RecordingGoogleCalendarClient(List<CalendarEvent> events) {
            this.events = events;
        }

        @Override
        public List<CalendarEvent> fetchUpcomingEvents(GoogleIntegration googleIntegration, LocalDateTime now, int limit) {
            this.lastIntegration = googleIntegration;
            this.lastLimit = limit;
            return events;
        }
    }

    private static class InMemoryGoogleIntegrationRepository implements GoogleIntegrationRepository {

        private GoogleIntegration googleIntegration;

        @Override
        public Optional<GoogleIntegration> findByUserId(String userId) {
            return Optional.ofNullable(googleIntegration);
        }

        @Override
        public void save(GoogleIntegration googleIntegration) {
            this.googleIntegration = googleIntegration;
        }
    }
}
