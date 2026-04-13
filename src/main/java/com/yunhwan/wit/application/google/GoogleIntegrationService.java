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

        Resolution resolution = resolveFetchableIntegration(userId, googleIntegration, now);
        List<CalendarEvent> calendarEvents = fetchUpcomingEvents(resolution.googleIntegration(), now, userId, resolution.refreshed());

        return new GoogleConnectionResult(true, resolution.googleIntegration(), calendarEvents);
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
                    LocalDateTime now = LocalDateTime.now(clock);
                    Resolution resolution = resolveFetchableIntegration(userId, googleIntegration, now);
                    List<CalendarEvent> calendarEvents = fetchUpcomingEvents(
                            resolution.googleIntegration(),
                            now,
                            userId,
                            resolution.refreshed()
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

    private Resolution resolveFetchableIntegration(
            String userId,
            GoogleIntegration googleIntegration,
            LocalDateTime now
    ) {
        GoogleAccessTokenStatus tokenStatus = googleIntegration.accessTokenStatus(now);
        log.info(
                "[GoogleIntegrationDebug] token status evaluated. userId={}, email={}, status={}",
                userId,
                googleIntegration.email(),
                tokenStatus
        );

        return switch (tokenStatus) {
            case ACTIVE -> new Resolution(googleIntegration, false);
            case EXPIRED_REFRESHABLE -> new Resolution(refreshIntegration(userId, googleIntegration), true);
            case EXPIRED_REAUTH_REQUIRED -> {
                log.info(
                        "[GoogleIntegrationDebug] re-auth required path triggered. userId={}, email={}, reason=expired-without-refresh-token",
                        userId,
                        googleIntegration.email()
                );
                throw new GoogleReauthenticationRequiredException("Google access token expired and re-authentication is required");
            }
        };
    }

    private GoogleIntegration refreshIntegration(String userId, GoogleIntegration googleIntegration) {
        try {
            log.info(
                    "[GoogleIntegrationDebug] refresh attempted. userId={}, email={}",
                    userId,
                    googleIntegration.email()
            );
            GoogleAccessTokenRefreshResult refreshResult = googleOAuthClient.refreshAccessToken(
                    googleIntegration.refreshToken()
            );
            log.info(
                    "[GoogleIntegrationDebug] refresh succeeded. userId={}, email={}, accessTokenPresent={}",
                    userId,
                    googleIntegration.email(),
                    refreshResult.accessToken() != null && !refreshResult.accessToken().isBlank()
            );

            GoogleIntegration refreshedIntegration = new GoogleIntegration(
                    googleIntegration.userId(),
                    googleIntegration.email(),
                    refreshResult.accessToken(),
                    googleIntegration.refreshToken(),
                    refreshResult.accessTokenExpiresAt(),
                    googleIntegration.connectedAt()
            );
            googleIntegrationRepository.save(refreshedIntegration);
            log.info(
                    "[GoogleIntegrationDebug] refreshed token persisted. userId={}, email={}",
                    userId,
                    refreshedIntegration.email()
            );
            return refreshedIntegration;
        } catch (GoogleReauthenticationRequiredException exception) {
            log.info(
                    "[GoogleIntegrationDebug] re-auth required path triggered. userId={}, email={}, reason=refresh-rejected",
                    userId,
                    googleIntegration.email()
            );
            throw exception;
        } catch (IllegalArgumentException exception) {
            log.info(
                    "[GoogleIntegrationDebug] re-auth required path triggered. userId={}, email={}, reason=refresh-impossible",
                    userId,
                    googleIntegration.email()
            );
            throw new GoogleReauthenticationRequiredException("Google refresh token is not available");
        } catch (GoogleIntegrationUnavailableException exception) {
            throw exception;
        }
    }

    private List<CalendarEvent> fetchUpcomingEvents(
            GoogleIntegration googleIntegration,
            LocalDateTime now,
            String userId,
            boolean refreshed
    ) {
        if (refreshed) {
            log.info(
                    "[GoogleIntegrationDebug] calendar fetch continued with refreshed token. userId={}, email={}",
                    userId,
                    googleIntegration.email()
            );
        }
        return googleCalendarClient.fetchUpcomingEvents(
                googleIntegration,
                now,
                DEFAULT_EVENT_LIMIT
        );
    }

    private record Resolution(
            GoogleIntegration googleIntegration,
            boolean refreshed
    ) {
    }
}
