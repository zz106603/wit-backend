package com.yunhwan.wit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("real-api")
@EnabledIfEnvironmentVariable(named = "RUN_GEMINI_REAL_API_TEST", matches = "true")
class GeminiLocationResolverRealIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private GeminiLocationResolver geminiLocationResolver;

    @Autowired
    private GeminiApiProperties properties;

    @Test
    void Gemini_실제_API로_강남_회식을_의미있는_위치로_해석한다() {
        Assumptions.assumeTrue(properties.apiKey() != null && !properties.apiKey().isBlank(),
                "GEMINI_API_KEY is required");
        Assumptions.assumeTrue(properties.model() != null && !properties.model().isBlank(),
                "GEMINI_MODEL is required");

        ResolvedLocation result = geminiLocationResolver.resolve("강남 회식");

        assertThat(result.status()).isIn(LocationResolutionStatus.RESOLVED, LocationResolutionStatus.APPROXIMATED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.AI);
        assertThat(result.normalizedQuery()).isNotBlank();
        assertThat(result.displayLocation()).isNotBlank();
        assertThat(result.lat()).isNotNull();
        assertThat(result.lng()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }
}
