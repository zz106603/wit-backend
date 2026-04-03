package com.yunhwan.wit.application.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingLocationResolverTest {

    @Test
    void 캐시히트면_delegate를_다시_호출하지_않는다() {
        CountingLocationResolver delegate = new CountingLocationResolver(resolved("강남 회식"));
        InMemoryLocationResolutionCache cache = new InMemoryLocationResolutionCache();
        cache.put("강남 회식", resolved("강남 회식"));
        CachingLocationResolver resolver = new CachingLocationResolver(delegate, cache);

        ResolvedLocation result = resolver.resolve("강남 회식");

        assertThat(result).isEqualTo(resolved("강남 회식"));
        assertThat(delegate.invocationCount()).isZero();
    }

    @Test
    void 캐시미스면_delegate결과를_캐시에_저장한다() {
        ResolvedLocation resolvedLocation = resolved("판교 미팅");
        CountingLocationResolver delegate = new CountingLocationResolver(resolvedLocation);
        InMemoryLocationResolutionCache cache = new InMemoryLocationResolutionCache();
        CachingLocationResolver resolver = new CachingLocationResolver(delegate, cache);

        ResolvedLocation result = resolver.resolve("판교 미팅");

        assertThat(result).isEqualTo(resolvedLocation);
        assertThat(delegate.invocationCount()).isEqualTo(1);
        assertThat(cache.find("판교 미팅")).contains(resolvedLocation);
    }

    @Test
    void failed결과도_다음호출에서는_캐시에서_반환한다() {
        ResolvedLocation failed = ResolvedLocation.failed("알수없는장소");
        CountingLocationResolver delegate = new CountingLocationResolver(failed);
        InMemoryLocationResolutionCache cache = new InMemoryLocationResolutionCache();
        CachingLocationResolver resolver = new CachingLocationResolver(delegate, cache);

        ResolvedLocation first = resolver.resolve("알수없는장소");
        ResolvedLocation second = resolver.resolve("알수없는장소");

        assertThat(first.status()).isEqualTo(failed.status());
        assertThat(second.status()).isEqualTo(failed.status());
        assertThat(delegate.invocationCount()).isEqualTo(1);
    }

    private ResolvedLocation resolved(String rawLocation) {
        return ResolvedLocation.resolved(
                rawLocation,
                "강남",
                "서울특별시 강남구",
                37.5172,
                127.0473,
                1.0,
                LocationResolvedBy.RULE
        );
    }

    private static final class CountingLocationResolver implements LocationResolver {

        private final AtomicInteger invocationCount = new AtomicInteger();
        private final ResolvedLocation result;

        private CountingLocationResolver(ResolvedLocation result) {
            this.result = result;
        }

        @Override
        public ResolvedLocation resolve(String rawLocation) {
            invocationCount.incrementAndGet();
            return result;
        }

        private int invocationCount() {
            return invocationCount.get();
        }
    }

    private static final class InMemoryLocationResolutionCache implements LocationResolutionCache {

        private final Map<String, ResolvedLocation> store = new HashMap<>();

        @Override
        public Optional<ResolvedLocation> find(String rawLocation) {
            return Optional.ofNullable(store.get(rawLocation));
        }

        @Override
        public void put(String rawLocation, ResolvedLocation resolvedLocation) {
            store.put(rawLocation, resolvedLocation);
        }
    }
}
