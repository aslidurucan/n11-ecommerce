package com.n11bootcamp.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN_IP = "unknown";

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedIp = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwardedIp != null && !forwardedIp.isBlank()) {
                String firstIp = forwardedIp.split(",")[0].trim();
                return Mono.just(firstIp);
            }

            String remoteIp = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse(UNKNOWN_IP);

            return Mono.just(remoteIp);
        };
    }
}