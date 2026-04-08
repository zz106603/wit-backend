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
import java.util.Set;
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
    private static final Set<String> GENERIC_TITLE_LOCATION_EXCLUSIONS = Set.of(
            "회의",
            "미팅",
            "운동",
            "생일",
            "생일축하합니다",
            "저녁약속",
            "점심약속",
            "약속",
            "점심",
            "저녁",
            "회식"
    );
    private static final Set<String> PLACE_LIKE_TITLE_HINTS = Set.of(
            "강남",
            "강남역",
            "잠실",
            "잠실새내",
            "성수",
            "판교",
            "홍대",
            "합정",
            "신촌",
            "여의도",
            "종로",
            "을지로",
            "건대",
            "사당",
            "교대",
            "역삼",
            "삼성",
            "선릉",
            "압구정",
            "신사",
            "이태원",
            "명동",
            "양재",
            "서초",
            "마포"
    );

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
                log.info(
                        "[GoogleCalendarDebug] Response body/items empty. bodyPresent={}, itemsPresent={}",
                        response != null,
                        response != null && response.items() != null
                );
                return List.of();
            }

            log.info("[GoogleCalendarDebug] Response items count: {}", response.items().size());

            List<CalendarEvent> calendarEvents = response.items().stream()
                    .map(this::toCalendarEventWithDebug)
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .toList();
            log.info("[GoogleCalendarDebug] Mapped calendar events count: {}", calendarEvents.size());
            return calendarEvents;
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
                locationCandidate(item.location(), item.summary())
        );
    }

    private String locationCandidate(String location, String summary) {
        if (StringUtils.hasText(location)) {
            return location;
        }

        if (isPlaceLikeSummary(summary)) {
            return summary;
        }

        return null;
    }

    private boolean isPlaceLikeSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return false;
        }

        String normalizedSummary = summary.replaceAll("[^0-9a-zA-Z가-힣]", "");
        if (!StringUtils.hasText(normalizedSummary)) {
            return false;
        }

        if (GENERIC_TITLE_LOCATION_EXCLUSIONS.contains(normalizedSummary)) {
            return false;
        }

        if (normalizedSummary.endsWith("역")) {
            return true;
        }

        return PLACE_LIKE_TITLE_HINTS.stream().anyMatch(normalizedSummary::contains);
    }

    private CalendarEvent toCalendarEventWithDebug(GoogleCalendarEventsResponse.GoogleCalendarEventItem item) {
        logGoogleCalendarRawItem(item);

        if (CANCELLED_STATUS.equals(item.status())) {
            log.info("[GoogleCalendarDebug] Event skipped. reason=cancelled, eventId={}", item.id());
            return null;
        }

        if (!StringUtils.hasText(item.id())) {
            log.info("[GoogleCalendarDebug] Event skipped. reason=blank-id, summary={}", item.summary());
            return null;
        }

        CalendarEvent calendarEvent = toCalendarEvent(item);
        if (calendarEvent == null) {
            log.info(
                    "[GoogleCalendarDebug] Event skipped. reason=start-missing-or-unparseable, eventId={}, summary={}, startDateTime={}, startDate={}",
                    item.id(),
                    item.summary(),
                    dateTime(item.start()),
                    date(item.start())
            );
            return null;
        }

        log.info(
                "[GoogleCalendarDebug] Event mapped. eventId={}, title={}, rawLocation={}, startAt={}, endAt={}",
                calendarEvent.eventId(),
                calendarEvent.title(),
                calendarEvent.rawLocation(),
                calendarEvent.startAt(),
                calendarEvent.endAt()
        );
        return calendarEvent;
    }

    private void logGoogleCalendarRawItem(GoogleCalendarEventsResponse.GoogleCalendarEventItem item) {
        log.info(
                "[GoogleCalendarDebug] Raw event item. eventId={}, status={}, summary={}, location={}, startDateTime={}, startDate={}, endDateTime={}, endDate={}",
                item.id(),
                item.status(),
                item.summary(),
                item.location(),
                dateTime(item.start()),
                date(item.start()),
                dateTime(item.end()),
                date(item.end())
        );
    }

    private String dateTime(GoogleCalendarEventsResponse.GoogleCalendarEventDateTime dateTime) {
        return dateTime == null ? null : dateTime.dateTime();
    }

    private String date(GoogleCalendarEventsResponse.GoogleCalendarEventDateTime dateTime) {
        return dateTime == null ? null : dateTime.date();
    }

    private LocalDateTime toLocalDateTime(GoogleCalendarEventsResponse.GoogleCalendarEventDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        if (StringUtils.hasText(dateTime.dateTime())) {
            ZoneId targetZone = ZoneId.of(properties.timeZone());
            LocalDateTime converted = OffsetDateTime.parse(dateTime.dateTime())
                    .atZoneSameInstant(targetZone)
                    .toLocalDateTime();
            log.info(
                    "[GoogleCalendarDebug] Event datetime converted. rawDateTime={}, targetZone={}, convertedDateTime={}",
                    dateTime.dateTime(),
                    targetZone,
                    converted
            );
            return converted;
        }

        if (StringUtils.hasText(dateTime.date())) {
            LocalDateTime converted = LocalDate.parse(dateTime.date()).atStartOfDay();
            log.info(
                    "[GoogleCalendarDebug] Event date converted. rawDate={}, targetZone={}, convertedDateTime={}",
                    dateTime.date(),
                    properties.timeZone(),
                    converted
            );
            return converted;
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
