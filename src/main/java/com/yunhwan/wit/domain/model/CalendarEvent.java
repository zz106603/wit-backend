package com.yunhwan.wit.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record CalendarEvent(
        String eventId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String rawLocation
) {

    public CalendarEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");

        if (endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("endAt must not be before startAt");
        }
    }
}
