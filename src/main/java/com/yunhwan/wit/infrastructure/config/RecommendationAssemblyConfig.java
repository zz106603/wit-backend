package com.yunhwan.wit.infrastructure.config;

import com.yunhwan.wit.application.location.AiLocationFallbackResolver;
import com.yunhwan.wit.application.location.CachingLocationResolver;
import com.yunhwan.wit.application.location.CurrentLocationProvider;
import com.yunhwan.wit.application.location.DefaultLocationResolver;
import com.yunhwan.wit.application.location.GooglePlacesLocationResolver;
import com.yunhwan.wit.application.location.LocationResolutionCache;
import com.yunhwan.wit.application.location.LocationResolver;
import com.yunhwan.wit.application.location.RuleBasedLocationResolver;
import com.yunhwan.wit.application.recommendation.RecommendationCache;
import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationService;
import com.yunhwan.wit.application.summary.SummaryGenerator;
import com.yunhwan.wit.application.weather.WeatherClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import java.time.Clock;
import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import com.yunhwan.wit.domain.rule.OutfitRuleEngine;
import com.yunhwan.wit.domain.rule.WeatherFailureFallbackDecisionProvider;
import com.yunhwan.wit.infrastructure.location.CurrentLocationProperties;
import com.yunhwan.wit.infrastructure.location.GooglePlacesProperties;
import com.yunhwan.wit.infrastructure.location.HttpGooglePlacesLocationResolver;
import com.yunhwan.wit.infrastructure.location.LocationCacheProperties;
import com.yunhwan.wit.infrastructure.location.RedisLocationResolutionCache;
import com.yunhwan.wit.infrastructure.ai.GeminiLocationResolver;
import com.yunhwan.wit.infrastructure.recommendation.RecommendationCacheProperties;
import com.yunhwan.wit.infrastructure.recommendation.RedisRecommendationCache;
import com.yunhwan.wit.infrastructure.summary.StubSummaryGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        CurrentLocationProperties.class,
        GooglePlacesProperties.class,
        LocationCacheProperties.class,
        RecommendationCacheProperties.class
})
public class RecommendationAssemblyConfig {

    @Bean
    public AiLocationFallbackResolver aiLocationFallbackResolver(GeminiLocationResolver geminiLocationResolver) {
        return geminiLocationResolver;
    }

    @Bean
    public RuleBasedLocationResolver ruleBasedLocationResolver() {
        return new RuleBasedLocationResolver();
    }

    @Bean
    public RestClient googlePlacesRestClient(RestClient.Builder builder, GooglePlacesProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    public GooglePlacesLocationResolver googlePlacesLocationResolver(
            RestClient googlePlacesRestClient,
            GooglePlacesProperties properties
    ) {
        return new HttpGooglePlacesLocationResolver(googlePlacesRestClient, properties);
    }

    @Bean
    public DefaultLocationResolver defaultLocationResolver(
            RuleBasedLocationResolver ruleBasedLocationResolver,
            GooglePlacesLocationResolver googlePlacesLocationResolver,
            AiLocationFallbackResolver aiLocationFallbackResolver
    ) {
        return new DefaultLocationResolver(
                ruleBasedLocationResolver,
                googlePlacesLocationResolver,
                aiLocationFallbackResolver
        );
    }

    @Bean
    public LocationResolutionCache locationResolutionCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            LocationCacheProperties properties
    ) {
        return new RedisLocationResolutionCache(redisTemplate, objectMapper, properties);
    }

    @Bean
    public LocationResolver locationResolver(
            DefaultLocationResolver defaultLocationResolver,
            LocationResolutionCache locationResolutionCache
    ) {
        return new CachingLocationResolver(defaultLocationResolver, locationResolutionCache);
    }

    @Bean
    public CurrentLocationProvider currentLocationProvider(CurrentLocationProperties properties) {
        return new CurrentLocationProvider() {
            @Override
            public ResolvedLocation getCurrentLocation() {
                return ResolvedLocation.resolved(
                        "현재 위치",
                        properties.normalizedQuery(),
                        properties.displayLocation(),
                        properties.lat(),
                        properties.lng(),
                        1.0,
                        LocationResolvedBy.RULE
                );
            }

            @Override
            public boolean hasRealCurrentLocation() {
                return !properties.isDefaultLocation();
            }
        };
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
    public RecommendationCache recommendationCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RecommendationCacheProperties properties
    ) {
        return new RedisRecommendationCache(redisTemplate, objectMapper, properties);
    }

    @Bean
    public RecommendationService recommendationService(
            LocationResolver locationResolver,
            CurrentLocationProvider currentLocationProvider,
            WeatherClient weatherClient,
            OutfitRuleEngine outfitRuleEngine,
            WeatherFailureFallbackDecisionProvider weatherFailureFallbackDecisionProvider,
            SummaryGenerator summaryGenerator,
            RecommendationCache recommendationCache,
            Clock clock
    ) {
        return new RecommendationService(
                locationResolver,
                currentLocationProvider,
                weatherClient,
                outfitRuleEngine,
                weatherFailureFallbackDecisionProvider,
                summaryGenerator,
                recommendationCache,
                clock
        );
    }

    @Bean
    public RecommendationHomeService recommendationHomeService(
            GoogleIntegrationService googleIntegrationService,
            RecommendationService recommendationService
    ) {
        return new RecommendationHomeService(googleIntegrationService, recommendationService);
    }
}
