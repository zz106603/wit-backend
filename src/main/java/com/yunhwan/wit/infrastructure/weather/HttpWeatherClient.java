package com.yunhwan.wit.infrastructure.weather;

import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.application.weather.WeatherForecastSnapshots;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpWeatherClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWeatherClient.class);
    private static final int OPEN_METEO_PAST_DAYS = 93;
    private static final int OPEN_METEO_FORECAST_DAYS = 15;

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
        return fetchSnapshot(location, requestedTargetTime, properties.currentPath(), false, "current");
    }

    @Override
    public WeatherSnapshot fetchWeatherAt(ResolvedLocation location, LocalDateTime targetTime) {
        validateLocation(location);
        Objects.requireNonNull(targetTime, "targetTime must not be null");

        return fetchWeatherRange(location, targetTime, targetTime).startWeather();
    }

    @Override
    public WeatherForecastSnapshots fetchWeatherRange(
            ResolvedLocation location,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        validateLocation(location);
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");

        LocalDateTime requestStartTime = startTime.isBefore(endTime) ? startTime : endTime;
        LocalDateTime requestEndTime = endTime.isAfter(startTime) ? endTime : startTime;
        validateForecastRangeOrThrow(requestStartTime, requestEndTime, "forecast-range");
        try {
            WeatherApiResponse response = fetchOpenMeteoResponse(
                    location,
                    requestStartTime,
                    properties.forecastPath(),
                    true,
                    requestEndTime,
                    "forecast-range"
            );
            return new WeatherForecastSnapshots(
                    weatherSnapshotMapper.toSnapshot(location, startTime, response),
                    weatherSnapshotMapper.toSnapshot(location, endTime, response)
            );
        } catch (RestClientResponseException exception) {
            log.warn(
                    "[RecommendationDebug] Open-Meteo request failed. purpose=forecast-range, status={}, responseBody={}",
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString()
            );
            throw new WeatherInfrastructureException("weather API request failed", exception);
        } catch (RestClientException exception) {
            log.warn(
                    "[RecommendationDebug] Open-Meteo communication failed. purpose=forecast-range, exceptionType={}, message={}",
                    exception.getClass().getName(),
                    exception.getMessage()
            );
            throw new WeatherInfrastructureException("weather API communication failed", exception);
        }
    }

    private void validateForecastRangeOrThrow(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String purpose
    ) {
        ForecastRangeValidation startValidation = validateForecastTime(startTime);
        ForecastRangeValidation endValidation = validateForecastTime(endTime);
        log.info(
                "[RecommendationDebug] Open-Meteo forecast range validation. purpose={}, startTime={}, startValid={}, startReason={}, endTime={}, endValid={}, endReason={}, minDate={}, maxDate={}",
                purpose,
                startTime,
                startValidation.valid(),
                startValidation.reason(),
                endTime,
                endValidation.valid(),
                endValidation.reason(),
                forecastMinDate(),
                forecastMaxDate()
        );

        if (!startValidation.valid() || !endValidation.valid()) {
            throw new WeatherInfrastructureException("forecast time is outside Open-Meteo allowed range");
        }
    }

    private ForecastRangeValidation validateForecastTime(LocalDateTime targetTime) {
        LocalDate targetDate = targetTime.toLocalDate();
        LocalDate minDate = forecastMinDate();
        LocalDate maxDate = forecastMaxDate();
        if (targetDate.isBefore(minDate)) {
            return new ForecastRangeValidation(false, "before-min-date");
        }
        if (targetDate.isAfter(maxDate)) {
            return new ForecastRangeValidation(false, "after-max-date");
        }
        return new ForecastRangeValidation(true, "within-range");
    }

    private LocalDate forecastMinDate() {
        return LocalDate.now(clock).minusDays(OPEN_METEO_PAST_DAYS);
    }

    private LocalDate forecastMaxDate() {
        return LocalDate.now(clock).plusDays(OPEN_METEO_FORECAST_DAYS);
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
            boolean includeTargetTime,
            String purpose
    ) {
        try {
            WeatherApiResponse response = fetchOpenMeteoResponse(
                    location,
                    requestedTargetTime,
                    path,
                    includeTargetTime,
                    requestedTargetTime,
                    purpose
            );
            return weatherSnapshotMapper.toSnapshot(location, requestedTargetTime, response);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "[RecommendationDebug] Open-Meteo request failed. purpose={}, status={}, responseBody={}",
                    purpose,
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString()
            );
            throw new WeatherInfrastructureException("weather API request failed", exception);
        } catch (RestClientException exception) {
            log.warn(
                    "[RecommendationDebug] Open-Meteo communication failed. purpose={}, exceptionType={}, message={}",
                    purpose,
                    exception.getClass().getName(),
                    exception.getMessage()
            );
            throw new WeatherInfrastructureException("weather API communication failed", exception);
        }
    }

    private WeatherApiResponse fetchOpenMeteoResponse(
            ResolvedLocation location,
            LocalDateTime startTime,
            String path,
            boolean includeTargetTime,
            LocalDateTime endTime,
            String purpose
    ) {
        log.info(
                "[RecommendationDebug] Open-Meteo request before. purpose={}, lat={}, lng={}, startTime={}, endTime={}, path={}",
                purpose,
                location.lat(),
                location.lng(),
                startTime,
                endTime,
                path
        );
        ResponseEntity<WeatherApiResponse> responseEntity = weatherRestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(path)
                            .queryParam("latitude", location.lat())
                            .queryParam("longitude", location.lng())
                            .queryParam("timezone", properties.timeZone());

                    if (includeTargetTime) {
                        LocalDateTime startHour = startTime.truncatedTo(ChronoUnit.HOURS);
                        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);
                        log.info(
                                "[RecommendationDebug] Open-Meteo forecast request params. purpose={}, startHour={}, endHour={}, hourlyFields={}, timezone={}",
                                purpose,
                                startHour,
                                endHour,
                                properties.hourlyFields(),
                                properties.timeZone()
                        );
                        uriBuilder.queryParam("hourly", properties.hourlyFields())
                                .queryParam("start_hour", startHour)
                                .queryParam("end_hour", endHour);
                    } else {
                        uriBuilder.queryParam("current", currentFields());
                    }

                    URI requestUri = uriBuilder.build();
                    log.info(
                            "[RecommendationDebug] Open-Meteo request URI. purpose={}, uri={}, currentFields={}, hourlyFields={}, timezone={}",
                            purpose,
                            fullRequestUrl(requestUri),
                            currentFields(),
                            properties.hourlyFields(),
                            properties.timeZone()
                    );
                    return requestUri;
                })
                .retrieve()
                .toEntity(WeatherApiResponse.class);
        WeatherApiResponse response = responseEntity.getBody();
        log.info(
                "[RecommendationDebug] Open-Meteo request after. purpose={}, status={}, startTime={}, endTime={}, hasCurrent={}, hasHourly={}",
                purpose,
                responseEntity.getStatusCode().value(),
                startTime,
                endTime,
                response != null && response.current() != null,
                response != null && response.hourly() != null
        );

        if (response == null) {
            throw new WeatherInfrastructureException("weather API returned empty response");
        }

        return response;
    }

    private String fullRequestUrl(URI requestUri) {
        if (requestUri.isAbsolute()) {
            return requestUri.toASCIIString();
        }
        return properties.baseUrl() + requestUri.toASCIIString();
    }

    private String currentFields() {
        if (!StringUtils.hasText(properties.currentFields())) {
            return properties.currentFields();
        }

        String sanitizedFields = Arrays.stream(properties.currentFields().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(field -> !"precipitation_probability".equals(field))
                .collect(Collectors.joining(","));
        if (!sanitizedFields.equals(properties.currentFields())) {
            log.info(
                    "[RecommendationDebug] Open-Meteo current fields sanitized. configured={}, actual={}",
                    properties.currentFields(),
                    sanitizedFields
            );
        }
        return sanitizedFields;
    }

    private record ForecastRangeValidation(boolean valid, String reason) {
    }
}
