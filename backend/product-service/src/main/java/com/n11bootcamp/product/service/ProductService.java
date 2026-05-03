package com.n11bootcamp.product.service;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductResponse getProduct(Long id, String language);

    Page<ProductResponse> listProducts(ProductFilterRequest filter, String language, Pageable pageable);

    List<String> getCategories();

    List<String> getBrands();

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);
}
