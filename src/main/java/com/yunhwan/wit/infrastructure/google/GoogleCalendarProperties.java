package com.yunhwan.wit.infrastructure.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.google.calendar")
public record GoogleCalendarProperties(
        String baseUrl,
        String eventsPath,
        String timeZone
) {
}
