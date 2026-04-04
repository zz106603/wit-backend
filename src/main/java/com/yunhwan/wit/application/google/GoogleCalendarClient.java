package com.yunhwan.wit.application.google;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

public interface GoogleCalendarClient {

    List<CalendarEvent> fetchUpcomingEvents(GoogleIntegration googleIntegration, LocalDateTime now, int limit);
}
