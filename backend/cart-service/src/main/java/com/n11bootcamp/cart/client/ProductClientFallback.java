package com.n11bootcamp.cart.client;

import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductClientFallback implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return (productId, language) -> {
            log.warn("product-service unreachable for productId={}: {}", productId, cause.getMessage());
            throw new ProductNotAvailableException(productId);
        };
    }
}
