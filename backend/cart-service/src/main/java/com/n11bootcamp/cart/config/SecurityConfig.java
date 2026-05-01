package com.n11bootcamp.cart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cart-service Spring Security konfigürasyonu.
 *
 * Bu servis Gateway'in arkasında çalışır. Resource server pattern uygulanır.
 *
 * Public endpoint'ler:
 *   - /actuator/health
 *   - /swagger-ui/**, /v3/api-docs/**
 *
 * Diğer her şey JWT gerektirir.
 *
 * Sorumluluk ayrımı:
 *  1. filterChain()                  → HTTP authorization rules
 *  2. jwtAuthenticationConverter()   → JWT → Spring authorities
 *  3. extractKeycloakRealmRoles()    → Keycloak-specific claim parse
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /**
     * JWT'yi Spring Security Authentication objesine çevirir.
     * Default converter scope'lara bakar; biz Keycloak realm rolleri istiyoruz.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractKeycloakRealmRoles);
        return converter;
    }

    /**
     * Keycloak JWT'sinde roller şu yapıda gelir:
     *   { "realm_access": { "roles": ["USER", "ADMIN"] } }
     *
     * Spring Security konvansiyonu: authority adı "ROLE_" prefix + büyük harf.
     * Örnek: "USER" → "ROLE_USER" → @PreAuthorize("hasRole('USER')") çalışır.
     */
    private Collection<GrantedAuthority> extractKeycloakRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }

        Object rawRoles = realmAccess.get(CLAIM_ROLES);
        if (!(rawRoles instanceof Collection<?> rolesCollection)) {
            return List.of();
        }

        return rolesCollection.stream()
            .map(role -> ROLE_PREFIX + role.toString().toUpperCase(Locale.ENGLISH))
            .map(SimpleGrantedAuthority::new)
            .map(authority -> (GrantedAuthority) authority)
            .toList();
    }
}
