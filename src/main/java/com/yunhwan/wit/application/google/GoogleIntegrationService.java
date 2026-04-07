package com.yunhwan.wit.application.google;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GoogleIntegrationService.class);
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
        log.info(
                "[GoogleIntegrationDebug] connect start. userId={}, repository={}",
                userId,
                System.identityHashCode(googleIntegrationRepository)
        );
        GoogleOAuthToken googleOAuthToken = googleOAuthClient.exchangeCode(command.code(), command.state());
        log.info(
                "[GoogleIntegrationDebug] token exchange success. userId={}, email={}, accessTokenPresent={}, refreshTokenPresent={}",
                userId,
                googleOAuthToken.email(),
                googleOAuthToken.accessToken() != null && !googleOAuthToken.accessToken().isBlank(),
                googleOAuthToken.refreshToken() != null && !googleOAuthToken.refreshToken().isBlank()
        );
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
        log.info(
                "[GoogleIntegrationDebug] integration save before. userId={}, email={}, repository={}",
                userId,
                googleIntegration.email(),
                System.identityHashCode(googleIntegrationRepository)
        );
        googleIntegrationRepository.save(googleIntegration);
        log.info(
                "[GoogleIntegrationDebug] integration save after. userId={}, email={}, repository={}",
                userId,
                googleIntegration.email(),
                System.identityHashCode(googleIntegrationRepository)
        );

        List<CalendarEvent> calendarEvents = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration,
                now,
                DEFAULT_EVENT_LIMIT
        );

        return new GoogleConnectionResult(true, googleIntegration, calendarEvents);
    }

    public List<CalendarEvent> getUpcomingEvents() {
        String userId = googleIntegrationUserProvider.getCurrentUserId();
        log.info(
                "[GoogleIntegrationDebug] integration lookup start. userId={}, repository={}",
                userId,
                System.identityHashCode(googleIntegrationRepository)
        );
        return googleIntegrationRepository.findByUserId(userId)
                .map(googleIntegration -> {
                    log.info(
                            "[GoogleIntegrationDebug] integration lookup hit. userId={}, email={}, repository={}",
                            userId,
                            googleIntegration.email(),
                            System.identityHashCode(googleIntegrationRepository)
                    );
                    log.info("[GoogleCalendarDebug] Google integration found. userId={}", userId);
                    List<CalendarEvent> calendarEvents = googleCalendarClient.fetchUpcomingEvents(
                            googleIntegration,
                            LocalDateTime.now(clock),
                            DEFAULT_EVENT_LIMIT
                    );
                    log.info(
                            "[GoogleCalendarDebug] Upcoming events fetched. userId={}, count={}",
                            userId,
                            calendarEvents.size()
                    );
                    return calendarEvents;
                })
                .orElseGet(() -> {
                    log.info(
                            "[GoogleIntegrationDebug] integration lookup miss. userId={}, repository={}",
                            userId,
                            System.identityHashCode(googleIntegrationRepository)
                    );
                    log.info("[GoogleCalendarDebug] Google integration not found. userId={}", userId);
                    return List.of();
                });
    }
}
