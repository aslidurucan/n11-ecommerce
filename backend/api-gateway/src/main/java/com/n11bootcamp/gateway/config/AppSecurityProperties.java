package com.n11bootcamp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * app.security.* YAML bloğunu güvenli şekilde bağlar.
 *
 * Neden @ConfigurationProperties?
 * @Value anotasyonu YAML liste formatını (- item) desteklemez;
 * Spring indexed key'leri (public-paths[0], [1]…) tek bir property
 * olarak görmez ve PlaceholderResolutionException fırlatır.
 * @ConfigurationProperties bu sorunu çözer.
 */
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private List<String> publicPaths;
    private List<String> publicGetPaths;

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getPublicGetPaths() {
        return publicGetPaths;
    }

    public void setPublicGetPaths(List<String> publicGetPaths) {
        this.publicGetPaths = publicGetPaths;
    }
}
