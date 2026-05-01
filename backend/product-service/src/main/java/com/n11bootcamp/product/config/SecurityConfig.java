package com.n11bootcamp.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            log.info("[JWT-CONVERTER] realm_access: {}", realmAccess);
            if (realmAccess == null) {
                log.warn("[JWT-CONVERTER] realm_access is NULL");
                return List.of();
            }
            Collection<?> rawRoles = (Collection<?>) realmAccess.get("roles");
            log.info("[JWT-CONVERTER] raw roles: {}", rawRoles);
            if (rawRoles == null) {
                log.warn("[JWT-CONVERTER] roles is NULL");
                return List.of();
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (Object role : rawRoles) {
                String authority = "ROLE_" + role.toString().toUpperCase(Locale.ENGLISH);
                authorities.add(new SimpleGrantedAuthority(authority));
                log.info("[JWT-CONVERTER] adding authority: {}", authority);
            }
            return authorities;
        });

        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
            );

        return http.build();
    }
}
