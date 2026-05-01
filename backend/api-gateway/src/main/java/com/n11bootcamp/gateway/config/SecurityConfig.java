package com.n11bootcamp.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.security.public-paths}")
    private List<String> publicPaths;

    @Value("${app.security.public-get-paths}")
    private List<String> publicGetPaths;

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(this::configureAuthorization)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    private void configureAuthorization(
            ServerHttpSecurity.AuthorizeExchangeSpec exchanges) {
        exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll();

        publicGetPaths.forEach(path ->
                exchanges.pathMatchers(HttpMethod.GET, path).permitAll());

        publicPaths.forEach(path ->
                exchanges.pathMatchers(path).permitAll());

        exchanges.anyExchange().authenticated();
    }
}