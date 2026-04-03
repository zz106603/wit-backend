package com.yunhwan.wit.application.recommendation;

import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RecommendationCache {

    Optional<RecommendationResult> find(CalendarEvent calendarEvent, LocalDateTime cacheTime);

    void put(CalendarEvent calendarEvent, LocalDateTime cacheTime, RecommendationResult recommendationResult);
}
