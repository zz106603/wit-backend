package com.yunhwan.wit.infrastructure.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public record WeatherApiResponse(
        String regionName,
        LocalDateTime targetTime,
        Integer temperature,
        Integer feelsLike,
        Integer precipitationProbability,
        String condition,
        CurrentWeather current,
        HourlyWeather hourly
) {

    public WeatherApiResponse(
            String regionName,
            LocalDateTime targetTime,
            Integer temperature,
            Integer feelsLike,
            Integer precipitationProbability,
            String condition
    ) {
        this(regionName, targetTime, temperature, feelsLike, precipitationProbability, condition, null, null);
    }

    public record CurrentWeather(
            LocalDateTime time,
            @JsonProperty("temperature_2m")
            Double temperature,
            @JsonProperty("apparent_temperature")
            Double apparentTemperature,
            @JsonProperty("precipitation_probability")
            Integer precipitationProbability,
            Double precipitation,
            Double rain,
            @JsonProperty("weather_code")
            Integer weatherCode
    ) {
    }

    public record HourlyWeather(
            List<LocalDateTime> time,
            @JsonProperty("temperature_2m")
            List<Double> temperature,
            @JsonProperty("apparent_temperature")
            List<Double> apparentTemperature,
            @JsonProperty("precipitation_probability")
            List<Integer> precipitationProbability,
            List<Double> precipitation,
            List<Double> rain,
            @JsonProperty("weather_code")
            List<Integer> weatherCode
    ) {
    }
}
