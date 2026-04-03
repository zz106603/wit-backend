package com.yunhwan.wit.infrastructure.config;

import com.yunhwan.wit.application.location.AiLocationFallbackResolver;
import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.DefaultLocationResolver;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.location.RuleBasedLocationResolver;
import com.yunhwan.wit.application.recommendation.RecommendationService;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import com.yunhwan.wit.domain.rule.WeatherFailureFallbackDecisionProvider;
import com.yunhwan.wit.infrastructure.location.CurrentLocationProperties;
import com.yunhwan.wit.infrastructure.summary.StubSummaryGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CurrentLocationProperties.class)
public class RecommendationAssemblyConfig {

    @Bean
    public AiLocationFallbackResolver aiLocationFallbackResolver() {
        return ResolvedLocation::failed;
    }

    @Bean
    public RuleBasedLocationResolver ruleBasedLocationResolver() {
        return new RuleBasedLocationResolver();
    }

    @Bean
    public LocationResolver locationResolver(
            RuleBasedLocationResolver ruleBasedLocationResolver,
            AiLocationFallbackResolver aiLocationFallbackResolver
    ) {
        return new DefaultLocationResolver(ruleBasedLocationResolver, aiLocationFallbackResolver);
    }

    @Bean
    public CurrentLocationProvider currentLocationProvider(CurrentLocationProperties properties) {
        return () -> ResolvedLocation.resolved(
                "현재 위치",
                properties.normalizedQuery(),
                properties.displayLocation(),
                properties.lat(),
                properties.lng(),
                1.0,
                LocationResolvedBy.RULE
        );
    }

    @Bean
    public OutfitRuleEngine outfitRuleEngine() {
        return new OutfitRuleEngine();
    }

    @Bean
    public WeatherFailureFallbackDecisionProvider weatherFailureFallbackDecisionProvider() {
        return new WeatherFailureFallbackDecisionProvider();
    }

    @Bean
    public SummaryGenerator summaryGenerator() {
        return new StubSummaryGenerator();
    }

    @Bean
    public RecommendationService recommendationService(
            LocationResolver locationResolver,
            CurrentLocationProvider currentLocationProvider,
            WeatherClient weatherClient,
            OutfitRuleEngine outfitRuleEngine,
            WeatherFailureFallbackDecisionProvider weatherFailureFallbackDecisionProvider,
            SummaryGenerator summaryGenerator
    ) {
        return new RecommendationService(
                locationResolver,
                currentLocationProvider,
                weatherClient,
                outfitRuleEngine,
                weatherFailureFallbackDecisionProvider,
                summaryGenerator
        );
    }
}
