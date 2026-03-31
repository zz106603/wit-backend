package com.yunhwan.wit.application.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultLocationResolverTest {

    private final RuleBasedLocationResolver ruleBasedLocationResolver = new RuleBasedLocationResolver();

    @Test
    void rule이_충분히_해결하면_ai_fallback을_호출하지_않는다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.failed("강남")
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("강남");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.RESOLVED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.RULE);
        assertThat(result.rawLocation()).isEqualTo("강남");
        assertThat(result.normalizedQuery()).isEqualTo("강남");
        assertThat(aiFallbackResolver.invocationCount()).isZero();
    }

    @Test
    void rule이_근사해결이면_ai_fallback_결과를_우선한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.resolved(
                        "강남 회식",
                        "강남",
                        "서울특별시 강남구",
                        37.5172,
                        127.0473,
                        0.82,
                        LocationResolvedBy.AI
                )
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("강남 회식");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.RESOLVED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.AI);
        assertThat(result.rawLocation()).isEqualTo("강남 회식");
        assertThat(result.displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.confidence()).isEqualTo(0.82);
        assertThat(aiFallbackResolver.invocationCount()).isEqualTo(1);
    }

    @Test
    void rule이_failed여도_ai가_근사해결하면_ai_결과를_반환한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.approximated(
                        "판교에서 미팅",
                        "판교",
                        "경기도 성남시 분당구 판교동",
                        37.3947,
                        127.1112,
                        0.75,
                        LocationResolvedBy.AI
                )
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("판교에서 미팅");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.APPROXIMATED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.AI);
        assertThat(result.rawLocation()).isEqualTo("판교에서 미팅");
        assertThat(result.normalizedQuery()).isEqualTo("판교");
        assertThat(result.displayLocation()).isEqualTo("경기도 성남시 분당구 판교동");
        assertThat(result.lat()).isEqualTo(37.3947);
        assertThat(result.lng()).isEqualTo(127.1112);
        assertThat(result.confidence()).isEqualTo(0.75);
        assertThat(aiFallbackResolver.invocationCount()).isEqualTo(1);
    }

    @Test
    void ai도_실패하면_기존_rule_근사결과를_유지한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.failed("강남 회식")
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("강남 회식");

        assertThat(result.rawLocation()).isEqualTo("강남 회식");
        assertThat(result.normalizedQuery()).isEqualTo("강남");
        assertThat(result.displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.RULE);
        assertThat(result.status()).isEqualTo(LocationResolutionStatus.APPROXIMATED);
        assertThat(aiFallbackResolver.invocationCount()).isEqualTo(1);
    }

    @Test
    void ai결과가_도메인규약에_맞지않으면_rule_결과를_유지한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.resolved(
                        "강남 회식",
                        "강남",
                        "서울특별시 강남구",
                        37.5172,
                        127.0473,
                        0.82,
                        LocationResolvedBy.RULE
                )
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("강남 회식");

        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.RULE);
        assertThat(result.status()).isEqualTo(LocationResolutionStatus.APPROXIMATED);
        assertThat(aiFallbackResolver.invocationCount()).isEqualTo(1);
    }

    @Test
    void rule과_ai가_모두_실패하면_failed를_반환한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.failed("알수없는장소")
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("알수없는장소");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.resolvedBy()).isNull();
        assertThat(result.rawLocation()).isEqualTo("알수없는장소");
        assertThat(result.normalizedQuery()).isNull();
        assertThat(result.displayLocation()).isNull();
        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(aiFallbackResolver.invocationCount()).isEqualTo(1);
    }

    @Test
    void blank입력은_ai_fallback없이_failed를_반환한다() {
        CountingAiLocationFallbackResolver aiFallbackResolver = new CountingAiLocationFallbackResolver(
                ResolvedLocation.failed("   ")
        );
        DefaultLocationResolver resolver = new DefaultLocationResolver(ruleBasedLocationResolver, aiFallbackResolver);

        ResolvedLocation result = resolver.resolve("   ");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.resolvedBy()).isNull();
        assertThat(result.rawLocation()).isEqualTo("   ");
        assertThat(aiFallbackResolver.invocationCount()).isZero();
    }

    private static class CountingAiLocationFallbackResolver implements AiLocationFallbackResolver {

        private final AtomicInteger invocationCount = new AtomicInteger();
        private final ResolvedLocation result;

        private CountingAiLocationFallbackResolver(ResolvedLocation result) {
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
}
