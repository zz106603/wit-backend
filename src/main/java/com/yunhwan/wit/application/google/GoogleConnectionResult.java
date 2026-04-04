package com.yunhwan.wit.application.google;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.util.List;

public record GoogleConnectionResult(
        boolean connected,
        GoogleIntegration googleIntegration,
        List<CalendarEvent> calendarEvents
) {
}
