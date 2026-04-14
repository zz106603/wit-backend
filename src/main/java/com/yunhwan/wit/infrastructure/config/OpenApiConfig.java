package com.yunhwan.wit.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI witOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wit Backend API")
                        .version("v1")
                        .description("iOS 연동용 MVP API 스펙")
                        .contact(new Contact().name("wit-backend")));
    }
}
