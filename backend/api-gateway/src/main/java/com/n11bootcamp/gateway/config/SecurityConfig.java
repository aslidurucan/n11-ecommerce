package com.n11bootcamp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final AppSecurityProperties securityProperties;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                          AppSecurityProperties securityProperties) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.securityProperties = securityProperties;
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

        securityProperties.getPublicGetPaths().forEach(path ->
                exchanges.pathMatchers(HttpMethod.GET, path).permitAll());

        securityProperties.getPublicPaths().forEach(path ->
                exchanges.pathMatchers(path).permitAll());

        exchanges.anyExchange().authenticated();
    }
}