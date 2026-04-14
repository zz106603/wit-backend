package com.yunhwan.wit.infrastructure.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/integrations/google/login-url").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/integrations/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/integrations/google/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/home").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/events/*").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
                .build();
    }
}
