package com.n11bootcamp.cart.client;

import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Product service için Feign fallback.
 *
 * <p>Hata türüne göre farklı exception'lar fırlatır — kullanıcıya doğru mesaj
 * gösterilebilsin diye:</p>
 * <ul>
 *   <li><b>404 Not Found</b> → ProductNotAvailableException → 422 (ürün yok/silinmiş)</li>
 *   <li><b>Diğer hatalar</b> (timeout, 5xx, circuit breaker open) →
 *       IllegalStateException → 503 (geçici servis sorunu)</li>
 * </ul>
 *
 * <p>Niye önemli? Kullanıcıya "ürün yok" denirse alışverişten vazgeçebilir.
 * Halbuki gerçekte sadece geçici servis sorunu var, retry yapsa düzelir.</p>
 */
@Component
@Slf4j
public class ProductClientFallback implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return (productId, language) -> {
            // 404 — ürün gerçekten yok
            if (cause instanceof FeignException.NotFound) {
                log.warn("Product not found in catalog: productId={}", productId);
                throw new ProductNotAvailableException(productId);
            }

            // Diğer her şey — geçici servis sorunu (timeout, 5xx, circuit breaker open)
            log.error("Product service temporarily unavailable for productId={}: {}",
                productId, cause.getMessage());
            throw new IllegalStateException(
                "Product service is temporarily unavailable. Please try again.", cause);
        };
    }
}
