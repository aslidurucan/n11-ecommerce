package com.n11bootcamp.product.service;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import com.n11bootcamp.product.entity.Product;
import com.n11bootcamp.product.entity.ProductTranslation;
import com.n11bootcamp.product.exception.ProductNotFoundException;
import com.n11bootcamp.product.mapper.ProductMapper;
import com.n11bootcamp.product.repository.ProductRepository;
import com.n11bootcamp.product.repository.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Cacheable(value = "products", key = "#id + '-' + #language")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id, String language) {
        Product product = findOrThrow(id);
        return productMapper.toResponse(product, language);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(ProductFilterRequest filter,
                                               String language, Pageable pageable) {
        Specification<Product> spec = buildSpec(filter);
        return productRepository.findAll(spec, pageable)
            .map(p -> productMapper.toResponse(p, language));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.builder()
            .category(request.category())
            .brand(request.brand())
            .basePrice(request.basePrice())
            .imageUrl(request.imageUrl())
            .build();

        request.translations().forEach(t ->
            product.addTranslation(ProductTranslation.builder()
                .language(t.language())
                .name(t.name())
                .description(t.description())
                .build())
        );

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, category={}", saved.getId(), saved.getCategory());
        return productMapper.toResponse(saved, "tr");
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);

        if (request.category() != null) product.setCategory(request.category());
        if (request.brand()    != null) product.setBrand(request.brand());
        if (request.basePrice()!= null) product.setBasePrice(request.basePrice());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.active()   != null) product.setActive(request.active());

        productRepository.save(product);
        log.info("Product updated: id={}", id);
        return productMapper.toResponse(product, "tr");
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }


    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Specification<Product> buildSpec(ProductFilterRequest filter) {
        Specification<Product> spec = ProductSpecification.isActive();

        if (filter.category() != null && !filter.category().isBlank()) {
            spec = spec.and(ProductSpecification.hasCategory(filter.category()));
        }
        if (filter.brand() != null && !filter.brand().isBlank()) {
            spec = spec.and(ProductSpecification.hasBrand(filter.brand()));
        }
        if (filter.minPrice() != null && filter.maxPrice() != null) {
            spec = spec.and(ProductSpecification.priceBetween(filter.minPrice(), filter.maxPrice()));
        } else if (filter.minPrice() != null) {
            spec = spec.and(ProductSpecification.priceAtLeast(filter.minPrice()));
        } else if (filter.maxPrice() != null) {
            spec = spec.and(ProductSpecification.priceAtMost(filter.maxPrice()));
        }

        return spec;
    }
}
