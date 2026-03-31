package com.yunhwan.wit.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.OutfitDecision;
import com.yunhwan.wit.domain.model.RecommendedOutfitLevel;
import com.yunhwan.wit.domain.model.WeatherSnapshot;
import com.yunhwan.wit.domain.model.WeatherType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutfitRuleEngineTest {

    private final OutfitRuleEngine outfitRuleEngine = new OutfitRuleEngine();

    @Test
    void 종료시점_강수확률이_50이면_우산이_필요하다() {
        OutfitDecision decision = decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(22, 22, 20, WeatherType.CLEAR),
                snapshot(20, 20, 50, WeatherType.CLOUDY)
        );

        assertThat(decision.needUmbrella()).isTrue();
        assertThat(decision.umbrellaReason()).contains("50%");
    }

    @Test
    void 종료시점_강수확률이_49이면_우산이_필요하지_않다() {
        OutfitDecision decision = decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(22, 22, 20, WeatherType.CLEAR),
                snapshot(20, 20, 49, WeatherType.CLOUDY)
        );

        assertThat(decision.needUmbrella()).isFalse();
        assertThat(decision.umbrellaReason()).contains("없어도 됩니다");
    }

    @Test
    void 종료시점이_비면_강수확률이_낮아도_우산이_필요하다() {
        OutfitDecision decision = decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(22, 22, 20, WeatherType.CLEAR),
                snapshot(21, 21, 10, WeatherType.RAIN)
        );

        assertThat(decision.needUmbrella()).isTrue();
        assertThat(decision.umbrellaReason()).contains("비 예보");
    }

    @ParameterizedTest
    @CsvSource({
            "23, SHORT_SLEEVE, 반팔",
            "22, SHORT_SLEEVE_WITH_LIGHT_OUTER, 반팔 + 얇은 겉옷",
            "20, SHORT_SLEEVE_WITH_LIGHT_OUTER, 반팔 + 얇은 겉옷",
            "19, LONG_SLEEVE, 긴팔",
            "17, LONG_SLEEVE, 긴팔",
            "16, LONG_SLEEVE_WITH_LIGHT_OUTER, 긴팔 + 가벼운 겉옷",
            "13, LONG_SLEEVE_WITH_LIGHT_OUTER, 긴팔 + 가벼운 겉옷",
            "12, HEAVY_OUTER, 두꺼운 겉옷"
    })
    void 종료시점_체감온도_경계값에_따라_기본_옷차림이_정해진다(
            int endFeelsLike,
            RecommendedOutfitLevel expectedLevel,
            String expectedText
    ) {
        OutfitDecision decision = decide(
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR),
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR),
                snapshot(endFeelsLike, endFeelsLike, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(expectedLevel);
        assertThat(decision.recommendedOutfitText()).isEqualTo(expectedText);
        assertThat(decision.outfitReason()).contains("종료 시점 체감온도 기준");
    }

    @Test
    void 현재보다_4도이상_낮으면_한단계_올린다() {
        OutfitDecision decision = decide(
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
        assertThat(decision.recommendedOutfitText()).isEqualTo("긴팔");
        assertThat(decision.outfitReason()).contains("현재보다 4도 이상 낮아");
        assertThat(decision.temperatureGap()).isEqualTo(-4);
        assertThat(decision.weatherChangeSummary()).contains("더 쌀쌀");
    }

    @Test
    void 현재보다_3도만_낮으면_이_조건만으로는_보정하지_않는다() {
        OutfitDecision decision = decide(
                snapshot(23, 23, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER);
        assertThat(decision.outfitReason()).contains("종료 시점 체감온도 기준");
        assertThat(decision.temperatureGap()).isEqualTo(-3);
    }

    @Test
    void 시작에서_종료까지_3도이상_떨어지면_한단계_올린다() {
        OutfitDecision decision = decide(
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(23, 23, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
        assertThat(decision.recommendedOutfitText()).isEqualTo("긴팔");
        assertThat(decision.outfitReason()).contains("3도 이상 내려가");
        assertThat(decision.weatherChangeSummary()).contains("기온이 내려갑니다");
    }

    @Test
    void 시작에서_종료까지_2도하락이면_이_조건만으로는_보정하지_않는다() {
        OutfitDecision decision = decide(
                snapshot(20, 20, 10, WeatherType.CLEAR),
                snapshot(22, 22, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER);
        assertThat(decision.outfitReason()).contains("종료 시점 체감온도 기준");
    }

    @Test
    void 저온과_비가_함께오면_한단계_올린다() {
        OutfitDecision decision = decide(
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(12, 12, 40, WeatherType.RAIN)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.HEAVY_OUTER);
        assertThat(decision.recommendedOutfitText()).isEqualTo("두꺼운 겉옷");
        assertThat(decision.outfitReason()).contains("낮은 체감온도와 비");
        assertThat(decision.needUmbrella()).isTrue();
    }

    @Test
    void 보정조건이_없으면_기본_옷차림을_유지한다() {
        OutfitDecision decision = decide(
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(21, 21, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER);
        assertThat(decision.recommendedOutfitText()).isEqualTo("반팔 + 얇은 겉옷");
        assertThat(decision.outfitReason()).contains("종료 시점 체감온도 기준");
        assertThat(decision.weatherChangeSummary()).contains("큰 날씨 변화는 없습니다");
    }

    @Test
    void 보정조건이_여러개여도_한단계만_올린다() {
        OutfitDecision decision = decide(
                snapshot(27, 27, 10, WeatherType.CLEAR),
                snapshot(24, 24, 10, WeatherType.CLEAR),
                snapshot(20, 20, 70, WeatherType.RAIN)
        );

        assertThat(decision.recommendedOutfitLevel()).isEqualTo(RecommendedOutfitLevel.LONG_SLEEVE);
        assertThat(decision.recommendedOutfitText()).isEqualTo("긴팔");
    }

    @Test
    void 추천단계와_추천문구는_항상_일치한다() {
        assertLevelAndText(RecommendedOutfitLevel.SHORT_SLEEVE, decide(
                snapshot(23, 23, 10, WeatherType.CLEAR),
                snapshot(23, 23, 10, WeatherType.CLEAR),
                snapshot(23, 23, 10, WeatherType.CLEAR)
        ), "반팔");

        assertLevelAndText(RecommendedOutfitLevel.SHORT_SLEEVE_WITH_LIGHT_OUTER, decide(
                snapshot(20, 20, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR),
                snapshot(20, 20, 10, WeatherType.CLEAR)
        ), "반팔 + 얇은 겉옷");

        assertLevelAndText(RecommendedOutfitLevel.LONG_SLEEVE, decide(
                snapshot(17, 17, 10, WeatherType.CLEAR),
                snapshot(17, 17, 10, WeatherType.CLEAR),
                snapshot(17, 17, 10, WeatherType.CLEAR)
        ), "긴팔");

        assertLevelAndText(RecommendedOutfitLevel.LONG_SLEEVE_WITH_LIGHT_OUTER, decide(
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(13, 13, 10, WeatherType.CLEAR),
                snapshot(13, 13, 10, WeatherType.CLEAR)
        ), "긴팔 + 가벼운 겉옷");

        assertLevelAndText(RecommendedOutfitLevel.HEAVY_OUTER, decide(
                snapshot(12, 12, 10, WeatherType.CLEAR),
                snapshot(12, 12, 10, WeatherType.CLEAR),
                snapshot(12, 12, 10, WeatherType.CLEAR)
        ), "두꺼운 겉옷");
    }

    private OutfitDecision decide(
            WeatherSnapshot currentWeather,
            WeatherSnapshot startWeather,
            WeatherSnapshot endWeather
    ) {
        return outfitRuleEngine.decide(currentWeather, startWeather, endWeather);
    }

    private void assertLevelAndText(
            RecommendedOutfitLevel expectedLevel,
            OutfitDecision decision,
            String expectedText
    ) {
        assertThat(decision.recommendedOutfitLevel()).isEqualTo(expectedLevel);
        assertThat(decision.recommendedOutfitText()).isEqualTo(expectedText);
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
