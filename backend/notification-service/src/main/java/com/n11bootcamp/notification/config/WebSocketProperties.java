package com.n11bootcamp.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * notification.websocket.* YAML bloğunu güvenli şekilde bağlar.
 *
 * @Value ile YAML liste formatı çalışmaz (PlaceholderResolutionException).
 * @ConfigurationProperties YAML list -> List<String> dönüşümünü doğru yapar.
 */
@Configuration
@ConfigurationProperties(prefix = "notification.websocket")
public class WebSocketProperties {

    private List<String> allowedOrigins;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
