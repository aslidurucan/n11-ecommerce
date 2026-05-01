package com.n11bootcamp.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket / STOMP konfigürasyonu.
 *
 * <p><b>Güvenlik:</b> setAllowedOriginPatterns yml'den whitelist okur.
 * Cross-Site WebSocket Hijacking (CSWSH) koruması — sadece bilinen
 * domain'lerden bağlantı kabul eder.</p>
 *
 * <p>Production deploy ederken yml'e gerçek domain eklenmeli, "*" KESİNLİKLE
 * kullanılmamalı (tüm sitelerden bağlantı = privacy ihlali).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${notification.websocket.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
            .withSockJS();
    }
}
