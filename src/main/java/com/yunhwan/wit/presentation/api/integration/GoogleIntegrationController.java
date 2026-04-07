package com.yunhwan.wit.presentation.api.integration;

import com.yunhwan.wit.application.google.GoogleCallbackCommand;
import com.yunhwan.wit.application.google.GoogleConnectionResult;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleLoginUrlResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/callback")
    public GoogleCallbackResponse callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        GoogleConnectionResult result = googleIntegrationService.connect(new GoogleCallbackCommand(code, state));
        return new GoogleCallbackResponse(
                result.connected(),
                result.googleIntegration().email(),
                result.calendarEvents().size()
        );
    }
}
