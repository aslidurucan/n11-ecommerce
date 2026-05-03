package com.n11bootcamp.chat.service;

import com.n11bootcamp.chat.client.ProductMetadataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMetadataServiceImpl implements ProductMetadataService {

    private final ProductMetadataClient productMetadataClient;

    @Override
    @Cacheable(value = "categories", unless = "#result.isEmpty()")
    public List<String> getCategories() {
        try {
            List<String> categories = productMetadataClient.getCategories();
            log.info("Fetched {} categories from product-service", categories.size());
            return categories;
        } catch (Exception e) {
            log.warn("Could not fetch categories from product-service: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @Cacheable(value = "brands", unless = "#result.isEmpty()")
    public List<String> getBrands() {
        try {
            List<String> brands = productMetadataClient.getBrands();
            log.info("Fetched {} brands from product-service", brands.size());
            return brands;
        } catch (Exception e) {
            log.warn("Could not fetch brands from product-service: {}", e.getMessage());
            return List.of();
        }
    }
}
