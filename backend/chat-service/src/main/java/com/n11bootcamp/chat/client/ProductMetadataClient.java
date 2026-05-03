package com.n11bootcamp.chat.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "product-service", contextId = "productMetadataClient")
public interface ProductMetadataClient {

    @GetMapping("/api/products/categories")
    List<String> getCategories();

    @GetMapping("/api/products/brands")
    List<String> getBrands();
}
