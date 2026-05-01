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

import java.util.Map;
import java.util.Optional;

@Component
public class JwtHeaderPropagationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-User-Username";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication())
            .filter(auth -> auth instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .map(jwtAuth -> {
                Jwt jwt = jwtAuth.getToken();

                String userId = jwt.getSubject();
                String username = jwt.getClaimAsString("preferred_username");

                String roles = Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                        .map(m -> (java.util.Collection<?>) m.get("roles"))
                        .map(c -> String.join(",", c.stream().map(Object::toString).toList()))
                        .orElse("");

                ServerHttpRequest mutated = exchange.getRequest().mutate()
                        .header(HEADER_USER_ID, userId == null ? "" : userId)
                        .header(HEADER_USERNAME, username == null ? "" : username)
                        .header(HEADER_USER_ROLES, roles)
                        .build();
                return exchange.mutate().request(mutated).build();
            })
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
