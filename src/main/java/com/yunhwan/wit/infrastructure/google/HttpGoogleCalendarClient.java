package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleCalendarClient;
import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class HttpGoogleCalendarClient implements GoogleCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(HttpGoogleCalendarClient.class);
    private static final String CANCELLED_STATUS = "cancelled";
    private static final String TIME_MAX_NOT_USED = "<not-used>";

    private final RestClient googleCalendarRestClient;
    private final GoogleCalendarProperties properties;

    public HttpGoogleCalendarClient(
            RestClient googleCalendarRestClient,
            GoogleCalendarProperties properties
    ) {
        this.googleCalendarRestClient = Objects.requireNonNull(
                googleCalendarRestClient,
                "googleCalendarRestClient must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public List<CalendarEvent> fetchUpcomingEvents(GoogleIntegration googleIntegration, LocalDateTime now, int limit) {
        Objects.requireNonNull(googleIntegration, "googleIntegration must not be null");
        Objects.requireNonNull(now, "now must not be null");

        try {
            String timeMin = toRfc3339(now);
            ResponseEntity<GoogleCalendarEventsResponse> responseEntity = googleCalendarRestClient.get()
                    .uri(uriBuilder -> {
                        URI requestUri = uriBuilder
                                .path(properties.eventsPath())
                                .queryParam("timeMin", timeMin)
                                .queryParam("singleEvents", true)
                                .queryParam("orderBy", "startTime")
                                .queryParam("maxResults", limit)
                                .queryParam("timeZone", properties.timeZone())
                                .build();
                        logGoogleCalendarRequestDebug(googleIntegration, requestUri, timeMin, limit);
                        return requestUri;
                    })
                    .headers(headers -> headers.setBearerAuth(googleIntegration.accessToken()))
                    .retrieve()
                    .toEntity(GoogleCalendarEventsResponse.class);
            log.info(
                    "[GoogleCalendarDebug] Response status code: {}",
                    responseEntity.getStatusCode().value()
            );

            GoogleCalendarEventsResponse response = responseEntity.getBody();

            if (response == null || response.items() == null) {
                return List.of();
            }

            return response.items().stream()
                    .filter(item -> !CANCELLED_STATUS.equals(item.status()))
                    .filter(item -> StringUtils.hasText(item.id()))
                    .map(this::toCalendarEvent)
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .toList();
        } catch (RestClientResponseException exception) {
            log.info("[GoogleCalendarDebug] Response status code: {}", exception.getStatusCode().value());
            throw new GoogleIntegrationInfrastructureException("Google Calendar request failed", exception);
        } catch (RestClientException exception) {
            throw new GoogleIntegrationInfrastructureException("Google Calendar communication failed", exception);
        }
    }

    private void logGoogleCalendarRequestDebug(
            GoogleIntegration googleIntegration,
            URI requestUri,
            String timeMin,
            int limit
    ) {
        log.info("[GoogleCalendarDebug] Base URL: {}", properties.baseUrl());
        log.info("[GoogleCalendarDebug] Path: {}", properties.eventsPath());
        log.info("[GoogleCalendarDebug] Calendar ID: {}", calendarId());
        log.info("[GoogleCalendarDebug] Raw datetime before URI building - timeMin={}, timeMax={}", timeMin, TIME_MAX_NOT_USED);
        log.info("[GoogleCalendarDebug] Full request URL: {}", fullRequestUrl(requestUri));
        log.info(
                "[GoogleCalendarDebug] Query params - timeMin={}, timeMax={}, singleEvents=true, orderBy=startTime, maxResults={}, timeZone={}",
                timeMin,
                TIME_MAX_NOT_USED,
                limit,
                properties.timeZone()
        );
        log.info("[GoogleCalendarDebug] Authorization header present: {}", StringUtils.hasText(googleIntegration.accessToken()));
        log.info("[GoogleCalendarDebug] Final encoded URL: {}", fullRequestUrl(requestUri));
    }

    private String fullRequestUrl(URI requestUri) {
        if (requestUri.isAbsolute()) {
            return requestUri.toASCIIString();
        }
        return properties.baseUrl() + requestUri.toASCIIString();
    }

    private String calendarId() {
        String marker = "/calendars/";
        int start = properties.eventsPath().indexOf(marker);
        int end = properties.eventsPath().lastIndexOf("/events");
        if (start < 0 || end <= start) {
            return "<unknown>";
        }
        return properties.eventsPath().substring(start + marker.length(), end);
    }

    private CalendarEvent toCalendarEvent(GoogleCalendarEventsResponse.GoogleCalendarEventItem item) {
        LocalDateTime startAt = toLocalDateTime(item.start());
        if (startAt == null) {
            return null;
        }

        LocalDateTime endAt = toLocalDateTime(item.end());
        if (endAt == null || endAt.isBefore(startAt)) {
            endAt = startAt;
        }

        return new CalendarEvent(
                item.id(),
                StringUtils.hasText(item.summary()) ? item.summary() : "제목 없음",
                startAt,
                endAt,
                item.location()
        );
    }

    private LocalDateTime toLocalDateTime(GoogleCalendarEventsResponse.GoogleCalendarEventDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        if (StringUtils.hasText(dateTime.dateTime())) {
            return OffsetDateTime.parse(dateTime.dateTime()).toLocalDateTime();
        }

        if (StringUtils.hasText(dateTime.date())) {
            return LocalDate.parse(dateTime.date()).atStartOfDay();
        }

        return null;
    }

    private String toRfc3339(LocalDateTime now) {
        return now.atZone(ZoneId.of(properties.timeZone()))
                .withZoneSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_INSTANT);
    }
}
