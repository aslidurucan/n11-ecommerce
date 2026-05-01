package com.n11bootcamp.product.service;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse getProduct(Long id, String language);

    Page<ProductResponse> listProducts(ProductFilterRequest filter, String language, Pageable pageable);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);
}
