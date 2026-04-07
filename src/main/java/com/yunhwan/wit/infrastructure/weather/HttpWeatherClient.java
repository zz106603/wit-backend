package com.yunhwan.wit.infrastructure.weather;

import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpWeatherClient implements WeatherClient {

    private final RestClient weatherRestClient;
    private final WeatherApiProperties properties;
    private final WeatherSnapshotMapper weatherSnapshotMapper;
    private final Clock clock;

    public HttpWeatherClient(
            RestClient weatherRestClient,
            WeatherApiProperties properties,
            WeatherSnapshotMapper weatherSnapshotMapper,
            Clock clock
    ) {
        this.weatherRestClient = Objects.requireNonNull(weatherRestClient, "weatherRestClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.weatherSnapshotMapper = Objects.requireNonNull(
                weatherSnapshotMapper,
                "weatherSnapshotMapper must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public WeatherSnapshot fetchCurrentWeather(ResolvedLocation location) {
        validateLocation(location);

        LocalDateTime requestedTargetTime = LocalDateTime.now(clock);
        return fetchSnapshot(location, requestedTargetTime, properties.currentPath(), false);
    }

    @Override
    public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
        validateLocation(location);
        Objects.requireNonNull(targetTime, "targetTime must not be null");

        return fetchSnapshot(location, targetTime, properties.forecastPath(), true);
    }

    private void validateLocation(ResolvedLocation location) {
        Objects.requireNonNull(location, "location must not be null");

        if (location.lat() == null || location.lng() == null) {
            throw new IllegalArgumentException("location coordinates must not be null");
        }
    }

    private WeatherSnapshot fetchSnapshot(
            ResolvedLocation location,
            LocalDateTime requestedTargetTime,
            String path,
            boolean includeTargetTime
    ) {
        try {
            WeatherApiResponse response = weatherRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("latitude", location.lat())
                                .queryParam("longitude", location.lng())
                                .queryParam("timezone", properties.timeZone());

                        if (includeTargetTime) {
                            uriBuilder.queryParam("hourly", properties.hourlyFields())
                                    .queryParam("start_hour", requestedTargetTime.truncatedTo(ChronoUnit.HOURS))
                                    .queryParam("end_hour", requestedTargetTime.truncatedTo(ChronoUnit.HOURS));
                        } else {
                            uriBuilder.queryParam("current", properties.currentFields());
                        }

                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(WeatherApiResponse.class);

            if (response == null) {
                throw new WeatherInfrastructureException("weather API returned empty response");
            }

            return weatherSnapshotMapper.toSnapshot(location, requestedTargetTime, response);
        } catch (RestClientResponseException exception) {
            throw new WeatherInfrastructureException("weather API request failed", exception);
        } catch (RestClientException exception) {
            throw new WeatherInfrastructureException("weather API communication failed", exception);
        }
    }
}
