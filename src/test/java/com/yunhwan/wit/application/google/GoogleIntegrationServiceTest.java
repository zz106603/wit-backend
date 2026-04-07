package com.yunhwan.wit.application.google;

import static org.assertj.core.api.Assertions.assertThat;

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
