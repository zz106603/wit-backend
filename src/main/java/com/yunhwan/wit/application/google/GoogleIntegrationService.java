package com.yunhwan.wit.application.google;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class GoogleIntegrationService {

    private static final int DEFAULT_EVENT_LIMIT = 3;

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleCalendarClient googleCalendarClient;
    private final GoogleIntegrationRepository googleIntegrationRepository;
    private final GoogleIntegrationUserProvider googleIntegrationUserProvider;
    private final Clock clock;

    public GoogleIntegrationService(
            GoogleOAuthClient googleOAuthClient,
            GoogleCalendarClient googleCalendarClient,
            GoogleIntegrationRepository googleIntegrationRepository,
            GoogleIntegrationUserProvider googleIntegrationUserProvider,
            Clock clock
    ) {
        this.googleOAuthClient = Objects.requireNonNull(googleOAuthClient, "googleOAuthClient must not be null");
        this.googleCalendarClient = Objects.requireNonNull(googleCalendarClient, "googleCalendarClient must not be null");
        this.googleIntegrationRepository = Objects.requireNonNull(
                googleIntegrationRepository,
                "googleIntegrationRepository must not be null"
        );
        this.googleIntegrationUserProvider = Objects.requireNonNull(
                googleIntegrationUserProvider,
                "googleIntegrationUserProvider must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public GoogleLoginUrlResult getLoginUrl() {
        return new GoogleLoginUrlResult(googleOAuthClient.buildLoginUrl());
    }

    public GoogleConnectionResult connect(GoogleCallbackCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        LocalDateTime now = LocalDateTime.now(clock);
        String userId = googleIntegrationUserProvider.getCurrentUserId();
        GoogleOAuthToken googleOAuthToken = googleOAuthClient.exchangeCode(command.code(), command.state());
        String refreshToken = googleOAuthToken.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            refreshToken = googleIntegrationRepository.findByUserId(userId)
                    .map(GoogleIntegration::refreshToken)
                    .orElseThrow(() -> new IllegalArgumentException("Google refresh token was not returned"));
        }
        GoogleIntegration googleIntegration = new GoogleIntegration(
                userId,
                googleOAuthToken.email(),
                googleOAuthToken.accessToken(),
                refreshToken,
                googleOAuthToken.accessTokenExpiresAt(),
                now
        );
        googleIntegrationRepository.save(googleIntegration);

        List<CalendarEvent> calendarEvents = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration,
                now,
                DEFAULT_EVENT_LIMIT
        );

        return new GoogleConnectionResult(true, googleIntegration, calendarEvents);
    }

    public List<CalendarEvent> getUpcomingEvents() {
        return googleIntegrationRepository.findByUserId(googleIntegrationUserProvider.getCurrentUserId())
                .map(googleIntegration -> googleCalendarClient.fetchUpcomingEvents(
                        googleIntegration,
                        LocalDateTime.now(clock),
                        DEFAULT_EVENT_LIMIT
                ))
                .orElse(List.of());
    }
}
