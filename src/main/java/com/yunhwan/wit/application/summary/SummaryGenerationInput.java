package com.yunhwan.wit.application.summary;

import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import java.util.Objects;

public record SummaryGenerationInput(
        OutfitDecision outfitDecision,
        WeatherSnapshot currentWeather,
        WeatherSnapshot startWeather,
        WeatherSnapshot endWeather
) {

    public SummaryGenerationInput {
        Objects.requireNonNull(outfitDecision, "outfitDecision must not be null");
    }
}
