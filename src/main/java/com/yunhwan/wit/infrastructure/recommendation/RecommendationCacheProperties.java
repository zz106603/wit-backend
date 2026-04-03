package com.yunhwan.wit.infrastructure.recommendation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.recommendation.cache")
public record RecommendationCacheProperties(
        Duration ttl
) {
}
