package com.yunhwan.wit.presentation.api.recommendation;

import com.yunhwan.wit.application.recommendation.RecommendationHomeService;
import com.yunhwan.wit.application.recommendation.RecommendationResult;
import com.yunhwan.wit.presentation.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "추천 조회 API")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationHomeService recommendationHomeService;

    public RecommendationController(RecommendationHomeService recommendationHomeService) {
        this.recommendationHomeService = recommendationHomeService;
    }

    @GetMapping("/home")
    @Operation(
            summary = "홈 추천 조회",
            description = "향후 일정 최대 3건에 대한 추천 목록을 반환한다. 각 일정 추천은 독립적으로 생성되며 일부 일정에서 추천 생성에 실패하면 해당 항목은 응답 목록에서 제외될 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = HomeRecommendationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Google 재연동 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Google 연동 장애",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public HomeRecommendationResponse getHomeRecommendations() {
        log.info("[RecommendationDebug] home request start");
        List<RecommendationResult> results = recommendationHomeService.getHomeRecommendations();
        HomeRecommendationResponse response = HomeRecommendationResponse.from(results);
        log.info("[RecommendationDebug] home request end. recommendationCount={}", results.size());
        return response;
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "이벤트 상세 추천 조회", description = "지정한 eventId의 추천 상세를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = RecommendationResponse.class))),
            @ApiResponse(responseCode = "404", description = "추천 대상 일정 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Google 재연동 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Google 연동 장애",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RecommendationResponse getEventRecommendation(@PathVariable String eventId) {
        log.info("[RecommendationDebug] event detail request start. eventId={}", eventId);
        RecommendationResponse response = RecommendationResponse.from(
                recommendationHomeService.getEventRecommendation(eventId)
        );
        log.info("[RecommendationDebug] event detail request end. eventId={}", eventId);
        return response;
    }
}
