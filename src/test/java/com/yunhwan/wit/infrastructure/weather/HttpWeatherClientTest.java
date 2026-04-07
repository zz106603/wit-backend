package com.yunhwan.wit.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.response.MockRestResponseCreators;

class HttpWeatherClientTest {

    private MockRestServiceServer server;
    private HttpWeatherClient weatherClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        weatherClient = new HttpWeatherClient(
                builder.baseUrl("https://weather.test").build(),
                new WeatherApiProperties(
                        "open-meteo",
                        "https://weather.test",
                        "/v1/forecast",
                        "/v1/forecast",
                        "Asia/Seoul",
                        "temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code",
                        "temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"
                ),
                new WeatherSnapshotMapper(),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void 현재날씨_스텁응답으로_snapshot을_생성한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andExpect(queryParam("latitude", "37.4979"))
                .andExpect(queryParam("longitude", "127.0276"))
                .andExpect(queryParam("timezone", "Asia/Seoul"))
                .andExpect(queryParam("current", "temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(withSuccess("""
                        {
                          "current": {
                            "time": "2026-04-01T09:00",
                            "temperature_2m": 18.2,
                            "apparent_temperature": 17.4,
                            "precipitation_probability": 20,
                            "precipitation": 0.0,
                            "rain": 0.0,
                            "weather_code": 0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherSnapshot snapshot = weatherClient.fetchCurrentWeather(resolvedLocation());

        assertThat(snapshot.regionName()).isEqualTo("서울특별시 강남구");
        assertThat(snapshot.targetTime()).isEqualTo(LocalDateTime.of(2026, 4, 1, 9, 0));
        assertThat(snapshot.temperature()).isEqualTo(18);
        assertThat(snapshot.feelsLike()).isEqualTo(17);
        assertThat(snapshot.precipitationProbability()).isEqualTo(20);
        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.CLEAR);
        server.verify();
    }

    @Test
    void 시작시각과_종료시각_예보를_각각_snapshot으로_생성한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 1, 18, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 1, 21, 0);

        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code&start_hour=2026-04-01T18:00&end_hour=2026-04-01T18:00"))
                .andExpect(queryParam("hourly", "temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andExpect(queryParam("start_hour", "2026-04-01T18:00"))
                .andExpect(queryParam("end_hour", "2026-04-01T18:00"))
                .andRespond(withSuccess("""
                        {
                          "hourly": {
                            "time": ["2026-04-01T18:00"],
                            "temperature_2m": [19.1],
                            "apparent_temperature": [17.8],
                            "precipitation_probability": [30],
                            "precipitation": [0.0],
                            "rain": [0.0],
                            "weather_code": [3]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code&start_hour=2026-04-01T21:00&end_hour=2026-04-01T21:00"))
                .andExpect(queryParam("start_hour", "2026-04-01T21:00"))
                .andRespond(withSuccess("""
                        {
                          "hourly": {
                            "time": ["2026-04-01T21:00"],
                            "temperature_2m": [16.2],
                            "apparent_temperature": [14.3],
                            "precipitation_probability": [70],
                            "precipitation": [2.0],
                            "rain": [2.0],
                            "weather_code": [61]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherSnapshot startSnapshot = weatherClient.fetchWeatherAt(resolvedLocation(), startTime);
        WeatherSnapshot endSnapshot = weatherClient.fetchWeatherAt(resolvedLocation(), endTime);

        assertThat(startSnapshot.targetTime()).isEqualTo(startTime);
        assertThat(startSnapshot.weatherType()).isEqualTo(WeatherType.CLOUDY);
        assertThat(endSnapshot.targetTime()).isEqualTo(endTime);
        assertThat(endSnapshot.temperature()).isEqualTo(16);
        assertThat(endSnapshot.feelsLike()).isEqualTo(14);
        assertThat(endSnapshot.precipitationProbability()).isEqualTo(70);
        assertThat(endSnapshot.weatherType()).isEqualTo(WeatherType.RAIN);
        server.verify();
    }

    @Test
    void provider_응답시각과_무관하게_요청시각을_targetTime으로_사용한다() {
        LocalDateTime requestedTime = LocalDateTime.of(2026, 4, 1, 18, 0);

        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code&start_hour=2026-04-01T18:00&end_hour=2026-04-01T18:00"))
                .andRespond(withSuccess("""
                        {
                          "hourly": {
                            "time": ["2026-04-01T18:00"],
                            "temperature_2m": [19.1],
                            "apparent_temperature": [17.8],
                            "precipitation_probability": [30],
                            "weather_code": [3]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherSnapshot snapshot = weatherClient.fetchWeatherAt(resolvedLocation(), requestedTime);

        assertThat(snapshot.targetTime()).isEqualTo(requestedTime);
        server.verify();
    }

    @Test
    void 알수없는_condition이면_UNKNOWN으로_처리한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(withSuccess("""
                        {
                          "regionName": "서울특별시 강남구",
                          "temperature": 18,
                          "feelsLike": 17,
                          "precipitationProbability": 20,
                          "condition": "FOG"
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherSnapshot snapshot = weatherClient.fetchCurrentWeather(resolvedLocation());

        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.UNKNOWN);
        server.verify();
    }

    @Test
    void 응답본문이_없으면_일관된_infra_예외로_변환한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherClient.fetchCurrentWeather(resolvedLocation()))
                .isInstanceOf(WeatherInfrastructureException.class)
                .hasMessage("weather API returned empty response");

        server.verify();
    }

    @Test
    void 역직렬화_실패를_일관된_infra_예외로_변환한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(withSuccess("{invalid-json}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> weatherClient.fetchCurrentWeather(resolvedLocation()))
                .isInstanceOf(WeatherInfrastructureException.class)
                .hasMessage("weather API communication failed");

        server.verify();
    }

    @Test
    void http_4xx를_일관된_infra_예외로_변환한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> weatherClient.fetchCurrentWeather(resolvedLocation()))
                .isInstanceOf(WeatherInfrastructureException.class)
                .hasMessage("weather API request failed");

        server.verify();
    }

    @Test
    void http_5xx를_일관된_infra_예외로_변환한다() {
        server.expect(requestTo("https://weather.test/v1/forecast?latitude=37.4979&longitude=127.0276&timezone=Asia/Seoul&current=temperature_2m,apparent_temperature,precipitation_probability,precipitation,rain,weather_code"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> weatherClient.fetchCurrentWeather(resolvedLocation()))
                .isInstanceOf(WeatherInfrastructureException.class)
                .hasMessage("weather API request failed");

        server.verify();
    }

    private ResolvedLocation resolvedLocation() {
        return ResolvedLocation.resolved(
                "강남 회식",
                "강남",
                "서울특별시 강남구",
                37.4979,
                127.0276,
                0.9,
                LocationResolvedBy.RULE
        );
    }
}
