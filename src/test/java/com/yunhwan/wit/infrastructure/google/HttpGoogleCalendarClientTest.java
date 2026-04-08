package com.yunhwan.wit.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.domain.model.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpGoogleCalendarClientTest {

    private MockRestServiceServer server;
    private HttpGoogleCalendarClient googleCalendarClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        googleCalendarClient = new HttpGoogleCalendarClient(
                builder.baseUrl("https://www.googleapis.test").build(),
                new GoogleCalendarProperties(
                        "https://www.googleapis.test",
                        "/calendar/v3/calendars/primary/events",
                        "Asia/Seoul"
                )
        );
    }

    @Test
    void Google_Calendar_7일이내_일정을_최대_3개_조회한다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(queryParam("timeMin", "2026-04-07T00:00:00Z"))
                .andExpect(queryParam("timeMax", "2026-04-14T00:00:00Z"))
                .andExpect(queryParam("singleEvents", "true"))
                .andExpect(queryParam("orderBy", "startTime"))
                .andExpect(queryParam("maxResults", "13"))
                .andExpect(queryParam("timeZone", "Asia/Seoul"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "저녁 회식",
                              "location": "강남",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T20:00:00+09:00" }
                            },
                            {
                              "id": "event-2",
                              "status": "confirmed",
                              "summary": "다음 미팅",
                              "location": "판교",
                              "start": { "dateTime": "2026-04-08T10:00:00+09:00" },
                              "end": { "dateTime": "2026-04-08T11:00:00+09:00" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).hasSize(2);
        assertThat(events.getFirst().eventId()).isEqualTo("event-1");
        assertThat(events.getFirst().title()).isEqualTo("저녁 회식");
        assertThat(events.getFirst().startAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 18, 0));
        assertThat(events.getFirst().endAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 20, 0));
        assertThat(events.getFirst().rawLocation()).isEqualTo("강남");
        server.verify();
    }

    @Test
    void 앞쪽_이벤트가_필터링되어도_뒤쪽_유효한_일정으로_최대_3개를_채운다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("maxResults", "13"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "cancelled-event",
                              "status": "cancelled",
                              "summary": "취소 일정",
                              "start": { "dateTime": "2026-04-07T10:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T11:00:00+09:00" }
                            },
                            {
                              "id": "invalid-event",
                              "status": "confirmed",
                              "summary": "시간 없음"
                            },
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "강남",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T19:00:00+09:00" }
                            },
                            {
                              "id": "event-2",
                              "status": "confirmed",
                              "summary": "판교",
                              "start": { "dateTime": "2026-04-08T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-08T19:00:00+09:00" }
                            },
                            {
                              "id": "event-3",
                              "status": "confirmed",
                              "summary": "홍대",
                              "start": { "dateTime": "2026-04-09T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-09T19:00:00+09:00" }
                            },
                            {
                              "id": "event-4",
                              "status": "confirmed",
                              "summary": "성수",
                              "start": { "dateTime": "2026-04-10T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-10T19:00:00+09:00" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).extracting(CalendarEvent::eventId)
                .containsExactly("event-1", "event-2", "event-3");
        server.verify();
    }

    @Test
    void Google_Calendar_dateTime_offset을_Asia_Seoul로_변환한다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "저녁 회식",
                              "location": "강남",
                              "start": { "dateTime": "2026-04-07T09:00:00Z" },
                              "end": { "dateTime": "2026-04-07T11:00:00Z" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().startAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 18, 0));
        assertThat(events.getFirst().endAt()).isEqualTo(LocalDateTime.of(2026, 4, 7, 20, 0));
        server.verify();
    }

    @Test
    void location_필드가_있으면_title보다_location을_rawLocation으로_사용한다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "강남",
                              "location": "판교",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T20:00:00+09:00" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().rawLocation()).isEqualTo("판교");
        server.verify();
    }

    @Test
    void location_필드가_없고_title이_장소처럼_보이면_title을_rawLocation_후보로_사용한다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "강남 목구멍",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T20:00:00+09:00" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().rawLocation()).isEqualTo("강남 목구멍");
        server.verify();
    }

    @Test
    void location_필드가_없고_title이_일반적인_제목이면_rawLocation으로_사용하지_않는다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "event-1",
                              "status": "confirmed",
                              "summary": "저녁 약속",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T20:00:00+09:00" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().rawLocation()).isNull();
        server.verify();
    }

    @Test
    void 취소된_일정과_시작시각이_없는_일정은_제외한다() {
        server.expect(requestTo(startsWith("https://www.googleapis.test/calendar/v3/calendars/primary/events")))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "cancelled-event",
                              "status": "cancelled",
                              "summary": "취소 일정",
                              "start": { "dateTime": "2026-04-07T18:00:00+09:00" },
                              "end": { "dateTime": "2026-04-07T20:00:00+09:00" }
                            },
                            {
                              "id": "invalid-event",
                              "status": "confirmed",
                              "summary": "시간 없음"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CalendarEvent> events = googleCalendarClient.fetchUpcomingEvents(
                googleIntegration(),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                3
        );

        assertThat(events).isEmpty();
        server.verify();
    }

    private GoogleIntegration googleIntegration() {
        return new GoogleIntegration(
                "default-user",
                "user@example.com",
                "access-token",
                "refresh-token",
                LocalDateTime.of(2026, 4, 7, 10, 0),
                LocalDateTime.of(2026, 4, 7, 9, 0)
        );
    }
}
