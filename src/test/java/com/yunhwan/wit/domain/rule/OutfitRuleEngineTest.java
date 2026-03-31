package com.yunhwan.wit.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class OutfitRuleEngineTest {

    private final OutfitRuleEngine outfitRuleEngine = new OutfitRuleEngine();

    @Test
    void 종료시점_강수확률이_50퍼센트면_우산이_필요하다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(22, 22, 10, WeatherType.CLEAR),
                snapshot(21, 21, 20, WeatherType.CLEAR),
                snapshot(20, 20, 50, WeatherType.CLOUDY)
        );

        assertThat(decision.needUmbrella()).isTrue();
    }

    @Test
    void 종료시점이_비면_강수확률과_무관하게_우산이_필요하다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(22, 22, 20, WeatherType.CLEAR),
                snapshot(21, 21, 10, WeatherType.RAIN)
        );

        assertThat(decision.needUmbrella()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "23, SHORT_SLEEVE, 반팔",
            "20, SHORT_SLEEVE_WITH_LIGHT_OUTER, 반팔 + 얇은 겉옷",
            "17, LONG_SLEEVE, 긴팔",
            "13, LONG_SLEEVE_WITH_LIGHT_OUTER, 긴팔 + 가벼운 겉옷",
            "12, HEAVY_OUTER, 두꺼운 겉옷"
    })
    void 종료시점_체감온도_경계값에_따라_기본_옷차림을_추천한다(
            int endFeelsLike,
            RecommendedOutfitLevel expectedLevel,
            String expectedText
    ) {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR),
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR),
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(expectedLevel);
        assertThat(decision.recommendedOutfitText()).isEqualTo(expectedText);
    }

    @Test
    void 현재보다_4도이상_낮으면_한단계_올린다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
    }

    @Test
    void 시작에서_종료까지_3도이상_떨어지면_한단계_올린다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(23, 23, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
    }

    @Test
    void 저온과_비가_함께오면_한단계_올린다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(12, 12, 40, WeatherType.RAIN)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
        assertThat(decision.needUmbrella()).isTrue();
    }

    @Test
    void 보정조건이_여러개여도_한단계만_올린다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(27, 27, 10, WeatherType.CLEAR),
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(20, 20, 70, WeatherType.RAIN)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
    }

    @Test
    void 이미_최고단계면_보정되어도_유지된다() {
        OutfitDecision decision = outfitRuleEngine.decide(
                snapshot(20, 20, 10, WeatherType.CLEAR),
                snapshot(16, 16, 10, WeatherType.CLEAR),
                snapshot(12, 12, 80, WeatherType.RAIN)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
    }

    private WeatherSnapshot snapshot(
            int temperature,
            int feelsLike,
            int precipitationProbability,
            WeatherType weatherType
    ) {
        return new WeatherSnapshot(
                "서울특별시 강남구",
                LocalDateTime.of(2026, 3, 31, 18, 0),
                temperature,
                feelsLike,
                precipitationProbability,
                weatherType
        );
    }
}
