package com.yunhwan.wit.infrastructure.location;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wit.location.cache")
public record LocationCacheProperties(
        Duration ttl
) {
}
