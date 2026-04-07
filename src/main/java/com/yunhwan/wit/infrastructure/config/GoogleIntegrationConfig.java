package com.yunhwan.wit.infrastructure.config;

import com.yunhwan.wit.application.google.GoogleCalendarClient;
import com.yunhwan.wit.application.google.GoogleIntegrationRepository;
import com.yunhwan.wit.application.google.GoogleIntegrationService;
import com.yunhwan.wit.application.google.GoogleOAuthClient;
import com.yunhwan.wit.application.google.GoogleIntegrationUserProvider;
import com.yunhwan.wit.infrastructure.google.GoogleCalendarProperties;
import com.yunhwan.wit.infrastructure.google.GoogleOAuthProperties;
import com.yunhwan.wit.infrastructure.google.HttpGoogleCalendarClient;
import com.yunhwan.wit.infrastructure.google.HttpGoogleOAuthClient;
import com.yunhwan.wit.infrastructure.google.InMemoryGoogleIntegrationRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({GoogleOAuthProperties.class, GoogleCalendarProperties.class})
public class GoogleIntegrationConfig {

    @Bean
    public RestClient googleOAuthRestClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RestClient googleCalendarRestClient(RestClient.Builder builder, GoogleCalendarProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    public GoogleOAuthClient googleOAuthClient(
            @Qualifier("googleOAuthRestClient") RestClient googleOAuthRestClient,
            GoogleOAuthProperties properties,
            Clock clock
    ) {
        return new HttpGoogleOAuthClient(googleOAuthRestClient, properties, clock);
    }

    @Bean
    public GoogleCalendarClient googleCalendarClient(
            @Qualifier("googleCalendarRestClient") RestClient googleCalendarRestClient,
            GoogleCalendarProperties properties
    ) {
        return new HttpGoogleCalendarClient(googleCalendarRestClient, properties);
    }

    @Bean
    public GoogleIntegrationRepository googleIntegrationRepository() {
        return new InMemoryGoogleIntegrationRepository();
    }

    @Bean
    public GoogleIntegrationUserProvider googleIntegrationUserProvider() {
        return () -> "default-user";
    }

    @Bean
    public GoogleIntegrationService googleIntegrationService(
            GoogleOAuthClient googleOAuthClient,
            GoogleCalendarClient googleCalendarClient,
            GoogleIntegrationRepository googleIntegrationRepository,
            GoogleIntegrationUserProvider googleIntegrationUserProvider,
            Clock clock
    ) {
        return new GoogleIntegrationService(
                googleOAuthClient,
                googleCalendarClient,
                googleIntegrationRepository,
                googleIntegrationUserProvider,
                clock
        );
    }
}
