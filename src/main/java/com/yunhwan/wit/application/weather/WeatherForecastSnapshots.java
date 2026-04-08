package com.yunhwan.wit.application.weather;

import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.util.Objects;

public record WeatherForecastSnapshots(
        WeatherSnapshot startWeather,
        WeatherSnapshot endWeather
) {

    public WeatherForecastSnapshots {
        Objects.requireNonNull(startWeather, "startWeather must not be null");
        Objects.requireNonNull(endWeather, "endWeather must not be null");
    }
}
