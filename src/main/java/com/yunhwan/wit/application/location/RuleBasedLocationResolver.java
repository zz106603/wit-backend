package com.yunhwan.wit.application.location;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleBasedLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedLocationResolver.class);

    private static final List<LocationRule> LOCATION_RULES = List.of(
            new LocationRule("강남", "서울특별시 강남구", 37.5172, 127.0473),
            new LocationRule("판교", "경기도 성남시 분당구 판교동", 37.3947, 127.1112),
            new LocationRule("여의도", "서울특별시 영등포구 여의도동", 37.5219, 126.9245)
    );

    public ResolvedLocation resolve(String rawLocation) {
        if (isMeaningless(rawLocation)) {
            log.info(
                    "[RecommendationDebug] rule location match. rawLocation={}, normalizedInput=null, result=rejected, reason=meaningless",
                    rawLocation
            );
            return ResolvedLocation.failed(rawLocation);
        }

        String normalizedRawLocation = normalize(rawLocation);
        log.info(
                "[RecommendationDebug] rule location match start. rawLocation={}, normalizedInput={}",
                rawLocation,
                normalizedRawLocation
        );

        for (LocationRule rule : LOCATION_RULES) {
            if (rule.matchesExact(normalizedRawLocation)) {
                log.info(
                        "[RecommendationDebug] rule location match. rawLocation={}, normalizedInput={}, result=matched, keyword={}, displayLocation={}, confidence=1.0",
                        rawLocation,
                        normalizedRawLocation,
                        rule.keyword(),
                        rule.displayLocation()
                );
                return ResolvedLocation.resolved(
                        rawLocation,
                        rule.keyword(),
                        rule.displayLocation(),
                        rule.lat(),
                        rule.lng(),
                        1.0,
                        LocationResolvedBy.RULE
                );
            }
        }

        log.info(
                "[RecommendationDebug] rule location match. rawLocation={}, normalizedInput={}, result=not-matched, reason=strict-exact-only",
                rawLocation,
                normalizedRawLocation
        );
        return ResolvedLocation.failed(rawLocation);
    }

    private boolean isMeaningless(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return true;
        }

        return normalize(rawLocation).isBlank();
    }

    private String normalize(String value) {
        Objects.requireNonNull(value, "value must not be null");

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-zA-Z가-힣]", "")
                .trim();
    }

    private record LocationRule(
            String keyword,
            String displayLocation,
            double lat,
            double lng
    ) {

        private boolean matchesExact(String normalizedRawLocation) {
            String normalizedKeyword = normalizeKeyword(keyword);
            String normalizedDisplayLocation = normalizeKeyword(displayLocation);
            return normalizedRawLocation.equals(normalizedKeyword) || normalizedRawLocation.equals(normalizedDisplayLocation);
        }

        private String normalizeKeyword(String value) {
            return value.toLowerCase(Locale.ROOT)
                    .replaceAll("[^0-9a-zA-Z가-힣]", "")
                    .trim();
        }
    }
}
