package com.yunhwan.wit.presentation.api.integration;

import com.yunhwan.wit.application.google.GoogleCallbackCommand;
import com.yunhwan.wit.application.google.GoogleConnectionResult;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleLoginUrlResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/google")
public class GoogleIntegrationController {

    private final GoogleIntegrationService googleIntegrationService;

    public GoogleIntegrationController(GoogleIntegrationService googleIntegrationService) {
        this.googleIntegrationService = googleIntegrationService;
    }

    @GetMapping("/login-url")
    public GoogleLoginUrlResponse getLoginUrl() {
        GoogleLoginUrlResult result = googleIntegrationService.getLoginUrl();
        return new GoogleLoginUrlResponse(result.loginUrl());
    }

    @PostMapping("/callback")
    public GoogleCallbackResponse callback(@Valid @RequestBody GoogleCallbackRequest request) {
        GoogleConnectionResult result = googleIntegrationService.connect(new GoogleCallbackCommand(request.code()));
        return new GoogleCallbackResponse(
                result.connected(),
                result.googleIntegration().email(),
                result.calendarEvents().size()
        );
    }
}
