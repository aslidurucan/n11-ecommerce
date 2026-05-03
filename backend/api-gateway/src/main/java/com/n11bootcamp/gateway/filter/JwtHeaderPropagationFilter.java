package com.n11bootcamp.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JwtHeaderPropagationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-User-Username";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final int FILTER_ORDER = -100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> propagateHeadersFromJwt(exchange, jwtAuth.getToken()))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private ServerWebExchange propagateHeadersFromJwt(ServerWebExchange exchange, Jwt jwt) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String roles = extractRolesFromKeycloakJwt(jwt);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, userId == null ? "" : userId)
                .header(HEADER_USERNAME, username == null ? "" : username)
                .header(HEADER_USER_ROLES, roles)
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private String extractRolesFromKeycloakJwt(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                .map(claim -> (Collection<?>) claim.get("roles"))
                .map(rolesCollection -> rolesCollection.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",")))
                .orElse("");
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}