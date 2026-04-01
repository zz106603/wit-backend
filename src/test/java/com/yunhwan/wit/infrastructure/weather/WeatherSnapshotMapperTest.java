package com.yunhwan.wit.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WeatherSnapshotMapperTest {

    private final WeatherSnapshotMapper mapper = new WeatherSnapshotMapper();

    @Test
    void 외부응답을_WeatherSnapshot으로_변환한다() {
        ResolvedLocation location = resolvedLocation();
        LocalDateTime requestedTime = LocalDateTime.of(2026, 4, 1, 18, 0);
        WeatherApiResponse response = new WeatherApiResponse(
                "서울특별시 강남구",
                LocalDateTime.of(2026, 4, 1, 18, 30),
                17,
                15,
                60,
                "CLOUDS"
        );

        WeatherSnapshot snapshot = mapper.toSnapshot(location, requestedTime, response);

        assertThat(snapshot.regionName()).isEqualTo("서울특별시 강남구");
        assertThat(snapshot.targetTime()).isEqualTo(requestedTime);
        assertThat(snapshot.temperature()).isEqualTo(17);
        assertThat(snapshot.feelsLike()).isEqualTo(15);
        assertThat(snapshot.precipitationProbability()).isEqualTo(60);
        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.CLOUDY);
    }

    @Test
    void 응답에_regionName과_targetTime이_없으면_내부기준값을_사용한다() {
        ResolvedLocation location = resolvedLocation();
        LocalDateTime requestedTime = LocalDateTime.of(2026, 4, 1, 9, 0);
        WeatherApiResponse response = new WeatherApiResponse(
                null,
                null,
                20,
                19,
                10,
                "CLEAR"
        );

        WeatherSnapshot snapshot = mapper.toSnapshot(location, requestedTime, response);

        assertThat(snapshot.regionName()).isEqualTo(location.displayLocation());
        assertThat(snapshot.targetTime()).isEqualTo(requestedTime);
        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    void 필수값이_누락되면_예외가_발생한다() {
        WeatherApiResponse response = new WeatherApiResponse(
                "서울특별시 강남구",
                null,
                20,
                null,
                30,
                "rain"
        );

        assertThatThrownBy(() -> mapper.toSnapshot(
                resolvedLocation(),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                response
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("feelsLike must not be null");
    }

    @Test
    void 지원하지_않는_condition이면_UNKNOWN으로_처리한다() {
        WeatherApiResponse response = new WeatherApiResponse(
                "서울특별시 강남구",
                null,
                20,
                19,
                30,
                "volcanic ash"
        );

        WeatherSnapshot snapshot = mapper.toSnapshot(
                resolvedLocation(),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                response
        );

        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.UNKNOWN);
    }

    @Test
    void condition이_비어있으면_UNKNOWN으로_처리한다() {
        WeatherApiResponse response = new WeatherApiResponse(
                "서울특별시 강남구",
                null,
                20,
                19,
                30,
                " "
        );

        WeatherSnapshot snapshot = mapper.toSnapshot(
                resolvedLocation(),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                response
        );

        assertThat(snapshot.weatherType()).isEqualTo(WeatherType.UNKNOWN);
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
