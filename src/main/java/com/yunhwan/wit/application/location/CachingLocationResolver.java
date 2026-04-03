package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingLocationResolver implements LocationResolver {

    private static final Logger log = LoggerFactory.getLogger(CachingLocationResolver.class);

    private final LocationResolver delegate;
    private final LocationResolutionCache locationResolutionCache;

    public CachingLocationResolver(
            LocationResolver delegate,
            LocationResolutionCache locationResolutionCache
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.locationResolutionCache = Objects.requireNonNull(
                locationResolutionCache,
                "locationResolutionCache must not be null"
        );
    }

    @Override
    public ResolvedLocation resolve(String rawLocation) {
        ResolvedLocation cached = readFromCache(rawLocation);
        if (cached != null) {
            return cached;
        }

        ResolvedLocation resolvedLocation = delegate.resolve(rawLocation);
        writeToCache(rawLocation, resolvedLocation);
        return resolvedLocation;
    }

    private ResolvedLocation readFromCache(String rawLocation) {
        try {
            return locationResolutionCache.find(rawLocation).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Location cache read failed. rawLocation={}", rawLocation, exception);
            return null;
        }
    }

    private void writeToCache(String rawLocation, ResolvedLocation resolvedLocation) {
        try {
            locationResolutionCache.put(rawLocation, resolvedLocation);
        } catch (RuntimeException exception) {
            log.warn("Location cache write failed. rawLocation={}, status={}",
                    rawLocation,
                    resolvedLocation.status(),
                    exception
            );
        }
    }
}
