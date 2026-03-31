package com.yunhwan.wit.application.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import org.junit.jupiter.api.Test;

class RuleBasedLocationResolverTest {

    private final RuleBasedLocationResolver resolver = new RuleBasedLocationResolver();

    @Test
    void 정확한_지역명이면_rule로_즉시_해결한다() {
        ResolvedLocation result = resolver.resolve("강남");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.RESOLVED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.RULE);
        assertThat(result.rawLocation()).isEqualTo("강남");
        assertThat(result.normalizedQuery()).isEqualTo("강남");
        assertThat(result.displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.lat()).isEqualTo(37.5172);
        assertThat(result.lng()).isEqualTo(127.0473);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void 자연어_문장에_지역키워드가_포함되면_근사해결한다() {
        ResolvedLocation result = resolver.resolve("강남 회식");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.APPROXIMATED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.RULE);
        assertThat(result.rawLocation()).isEqualTo("강남 회식");
        assertThat(result.normalizedQuery()).isEqualTo("강남");
        assertThat(result.displayLocation()).isEqualTo("서울특별시 강남구");
        assertThat(result.lat()).isEqualTo(37.5172);
        assertThat(result.lng()).isEqualTo(127.0473);
        assertThat(result.confidence()).isEqualTo(0.6);
    }

    @Test
    void 공백입력이면_failed를_반환한다() {
        ResolvedLocation result = resolver.resolve("   ");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("   ");
        assertThat(result.normalizedQuery()).isNull();
        assertThat(result.displayLocation()).isNull();
        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.resolvedBy()).isNull();
    }

    @Test
    void 규칙에_없는_위치는_failed를_반환한다() {
        ResolvedLocation result = resolver.resolve("알수없는장소");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("알수없는장소");
        assertThat(result.normalizedQuery()).isNull();
        assertThat(result.displayLocation()).isNull();
        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.resolvedBy()).isNull();
    }

    @Test
    void 특수문자만_있는_입력이면_failed를_반환한다() {
        ResolvedLocation result = resolver.resolve("!!!@@@");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("!!!@@@");
        assertThat(result.normalizedQuery()).isNull();
        assertThat(result.displayLocation()).isNull();
        assertThat(result.lat()).isNull();
        assertThat(result.lng()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.resolvedBy()).isNull();
    }
}
