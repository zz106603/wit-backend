package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleCalendarClient;
import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

public class StubGoogleCalendarClient implements GoogleCalendarClient {

    @Override
    public List<CalendarEvent> fetchUpcomingEvents(GoogleIntegration googleIntegration, LocalDateTime now, int limit) {
        return List.of();
    }
}
