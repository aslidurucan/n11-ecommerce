package com.n11bootcamp.cart.client;

import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductClientFallback implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return (productId, language) -> {
            if (cause instanceof FeignException.NotFound) {
                log.warn("Product not found in catalog: productId={}", productId);
                throw new ProductNotAvailableException(productId);
            }

            log.error("Product service temporarily unavailable for productId={}: {}",
                productId, cause.getMessage());
            throw new IllegalStateException(
                "Product service is temporarily unavailable. Please try again.", cause);
        };
    }
}
