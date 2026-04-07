package com.yunhwan.wit.infrastructure.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yunhwan.wit.domain.model.LocationResolvedBy;
import com.yunhwan.wit.domain.model.LocationResolutionStatus;
import com.yunhwan.wit.domain.model.ResolvedLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpGooglePlacesLocationResolverTest {

    private MockRestServiceServer server;
    private HttpGooglePlacesLocationResolver resolver;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesProperties properties = new GooglePlacesProperties(
                "test-api-key",
                "https://places.test",
                "/v1/places:searchText",
                "ko",
                "KR",
                1,
                "places.id,places.displayName,places.formattedAddress,places.location"
        );

        resolver = new HttpGooglePlacesLocationResolver(builder.baseUrl("https://places.test").build(), properties);
    }

    @Test
    void text_search_응답을_ResolvedLocation으로_변환한다() {
        server.expect(requestTo("https://places.test/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location"))
                .andExpect(content().json("""
                        {
                          "textQuery": "강남 목구멍",
                          "languageCode": "ko",
                          "regionCode": "KR",
                          "pageSize": 1
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "places": [
                            {
                              "id": "places/abc",
                              "displayName": { "text": "목구멍 강남점" },
                              "formattedAddress": "서울특별시 강남구 테헤란로 1",
                              "location": {
                                "latitude": 37.5001,
                                "longitude": 127.0362
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ResolvedLocation result = resolver.resolve("강남 목구멍");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.RESOLVED);
        assertThat(result.resolvedBy()).isEqualTo(LocationResolvedBy.GOOGLE_PLACES);
        assertThat(result.rawLocation()).isEqualTo("강남 목구멍");
        assertThat(result.normalizedQuery()).isEqualTo("목구멍 강남점");
        assertThat(result.displayLocation()).isEqualTo("서울특별시 강남구 테헤란로 1");
        assertThat(result.lat()).isEqualTo(37.5001);
        assertThat(result.lng()).isEqualTo(127.0362);
        assertThat(result.confidence()).isEqualTo(0.85);
        server.verify();
    }

    @Test
    void 결과가_없으면_failed를_반환한다() {
        server.expect(requestTo("https://places.test/v1/places:searchText"))
                .andRespond(withSuccess("""
                        { "places": [] }
                        """, MediaType.APPLICATION_JSON));

        ResolvedLocation result = resolver.resolve("알수없는장소");

        assertThat(result.status()).isEqualTo(LocationResolutionStatus.FAILED);
        assertThat(result.rawLocation()).isEqualTo("알수없는장소");
        server.verify();
    }

    @Test
    void places_api_실패는_infra_예외로_정규화한다() {
        server.expect(requestTo("https://places.test/v1/places:searchText"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> resolver.resolve("강남 목구멍"))
                .isInstanceOf(GooglePlacesInfrastructureException.class)
                .hasMessage("Google Places request failed");

        server.verify();
    }
}
