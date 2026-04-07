package com.yunhwan.wit.infrastructure.google;

import java.util.List;

record GoogleCalendarEventsResponse(
        List<GoogleCalendarEventItem> items
) {

    record GoogleCalendarEventItem(
            String id,
            String status,
            String summary,
            String location,
            GoogleCalendarEventDateTime start,
            GoogleCalendarEventDateTime end
    ) {
    }

    record GoogleCalendarEventDateTime(
            String dateTime,
            String date
    ) {
    }
}
