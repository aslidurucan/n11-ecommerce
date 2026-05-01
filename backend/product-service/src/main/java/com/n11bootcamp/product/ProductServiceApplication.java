package com.n11bootcamp.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Product Service - katalog yönetimi.
 *
 * Özellikler:
 * - Pagination + sıralama + arama
 * - i18n (Product + ProductTranslation tablosu) — Accept-Language header'ı
 * - Redis cache (10dk TTL) - sık erişilen ürünler için
 * - Specification pattern ile dinamik filtre (kategori, marka, fiyat aralığı)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
