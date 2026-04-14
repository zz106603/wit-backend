package com.yunhwan.wit.presentation.api.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhwan.wit.application.google.GoogleConnectionResult;
import com.yunhwan.wit.application.google.GoogleIntegrationUnavailableException;
import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleLoginUrlResult;
import com.yunhwan.wit.application.google.GoogleReauthenticationRequiredException;
import com.yunhwan.wit.infrastructure.config.SecurityConfig;
import com.yunhwan.wit.presentation.api.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GoogleIntegrationController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class GoogleIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleIntegrationService googleIntegrationService;

    @Test
    void 로그인_URL_조회는_200을_반환한다() throws Exception {
        given(googleIntegrationService.getLoginUrl())
                .willReturn(new GoogleLoginUrlResult("https://accounts.google.com/o/oauth2/v2/auth"));

        mockMvc.perform(get("/api/integrations/google/login-url"))
                .andExpect(status().isOk());
    }

    @Test
    void callback_정상요청은_200을_반환한다() throws Exception {
        given(googleIntegrationService.connect(any()))
                .willReturn(new GoogleConnectionResult(
                        true,
                        new GoogleIntegration(
                                "default-user",
                                "user@wit.local",
                                "access-token",
                                "refresh-token",
                                LocalDateTime.of(2026, 4, 4, 10, 0),
                                LocalDateTime.of(2026, 4, 4, 9, 0)
                        ),
                        List.of()
                ));

        mockMvc.perform(get("/api/integrations/google/callback")
                        .param("code", "oauth-code")
                        .param("state", "oauth-state"))
                .andExpect(status().isOk());
    }

    @Test
    void callback_code가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/integrations/google/callback")
                        .param("state", "oauth-state"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 재인증이_필요하면_401과_전용_error_code를_반환한다() throws Exception {
        given(googleIntegrationService.connect(any()))
                .willThrow(new GoogleReauthenticationRequiredException("Google access token expired and re-authentication is required"));

        mockMvc.perform(get("/api/integrations/google/callback")
                        .param("code", "oauth-code")
                        .param("state", "oauth-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_401"))
                .andExpect(jsonPath("$.message").value("Google access token expired and re-authentication is required"));
    }

    @Test
    void 외부_연동_장애면_503과_전용_error_code를_반환한다() throws Exception {
        given(googleIntegrationService.connect(any()))
                .willThrow(new GoogleIntegrationUnavailableException("Google integration is temporarily unavailable"));

        mockMvc.perform(get("/api/integrations/google/callback")
                        .param("code", "oauth-code")
                        .param("state", "oauth-state"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_503"))
                .andExpect(jsonPath("$.message").value("Google integration is temporarily unavailable"));
    }

    @Test
    void 다른_API는_인증없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/recommendations/events/event-1"))
                .andExpect(status().isUnauthorized());
    }
}
