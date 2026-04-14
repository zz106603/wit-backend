package com.yunhwan.wit.presentation.api.integration;

import com.yunhwan.wit.application.google.GoogleCallbackCommand;
import com.yunhwan.wit.application.google.GoogleConnectionResult;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleLoginUrlResult;
import com.yunhwan.wit.presentation.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/google")
@Tag(name = "Google Integration", description = "Google OAuth / Calendar 연동 API")
public class GoogleIntegrationController {

    private final GoogleIntegrationService googleIntegrationService;

    public GoogleIntegrationController(GoogleIntegrationService googleIntegrationService) {
        this.googleIntegrationService = googleIntegrationService;
    }

    @GetMapping("/login-url")
    @Operation(summary = "Google 로그인 URL 조회", description = "iOS가 Google OAuth 진입에 사용할 로그인 URL을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = GoogleLoginUrlResponse.class)))
    })
    public GoogleLoginUrlResponse getLoginUrl() {
        GoogleLoginUrlResult result = googleIntegrationService.getLoginUrl();
        return new GoogleLoginUrlResponse(result.loginUrl());
    }

    @GetMapping("/callback")
    public GoogleCallbackResponse callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        return connect(new GoogleCallbackRequest(code, state));
    }

    @PostMapping("/callback")
    @Operation(summary = "Google OAuth callback 처리", description = "iOS가 받은 authorization code/state를 서버에 전달해 Google 연동을 완료한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연동 성공",
                    content = @Content(schema = @Schema(implementation = GoogleCallbackResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Google 재연동 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Google 연동 장애",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public GoogleCallbackResponse connect(
            @Valid @RequestBody GoogleCallbackRequest request
    ) {
        GoogleConnectionResult result = googleIntegrationService.connect(
                new GoogleCallbackCommand(request.code(), request.state())
        );
        return new GoogleCallbackResponse(
                result.connected(),
                result.googleIntegration().email(),
                result.calendarEvents().size()
        );
    }
}
