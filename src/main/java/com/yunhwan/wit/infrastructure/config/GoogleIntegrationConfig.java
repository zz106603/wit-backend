package com.yunhwan.wit.infrastructure.config;

import com.yunhwan.wit.application.google.GoogleCalendarClient;
import com.yunhwan.wit.application.google.GoogleIntegrationRepository;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.infrastructure.google.GoogleOAuthProperties;
import com.yunhwan.wit.infrastructure.google.InMemoryGoogleIntegrationRepository;
import com.yunhwan.wit.infrastructure.google.StubGoogleCalendarClient;
import com.yunhwan.wit.infrastructure.google.StubGoogleOAuthClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleIntegrationConfig {

    @Bean
    public GoogleOAuthClient googleOAuthClient(GoogleOAuthProperties properties, Clock clock) {
        return new StubGoogleOAuthClient(properties, clock);
    }

    @Bean
    public GoogleCalendarClient googleCalendarClient() {
        return new StubGoogleCalendarClient();
    }

    @Bean
    public GoogleIntegrationRepository googleIntegrationRepository() {
        return new InMemoryGoogleIntegrationRepository();
    }

    @Bean
    public GoogleIntegrationService googleIntegrationService(
            GoogleOAuthClient googleOAuthClient,
            GoogleCalendarClient googleCalendarClient,
            GoogleIntegrationRepository googleIntegrationRepository,
            Clock clock
    ) {
        return new GoogleIntegrationService(
                googleOAuthClient,
                googleCalendarClient,
                googleIntegrationRepository,
                clock
        );
    }
}
