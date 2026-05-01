package com.n11bootcamp.product.service;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import com.n11bootcamp.product.entity.Product;
import com.n11bootcamp.product.exception.ProductNotFoundException;
import com.n11bootcamp.product.mapper.ProductMapper;
import com.n11bootcamp.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void getProduct_whenProductExists_returnsProductResponse() {
        Product product = buildProduct(1L, "Elektronik", "Apple", new BigDecimal("1000.00"));
        ProductResponse expected = buildResponse(1L, "iPhone", "Elektronik");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product, "tr")).thenReturn(expected);

        ProductResponse result = productService.getProduct(1L, "tr");

        assertThat(result).isEqualTo(expected);
        verify(productRepository).findById(1L);
        verify(productMapper).toResponse(product, "tr");
    }

    @Test
    void getProduct_whenProductNotFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L, "tr"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(productMapper, never()).toResponse(any(), any());
    }

    @Test
    void createProduct_whenValidRequest_savesAndReturnsResponse() {
        CreateProductRequest request = new CreateProductRequest(
                "Elektronik", "Samsung",
                new BigDecimal("500.00"), "http://img.com/s.jpg",
                List.of(new CreateProductRequest.TranslationRequest("tr", "Galaxy S24", "Akıllı telefon"))
        );

        Product saved = buildProduct(1L, "Elektronik", "Samsung", new BigDecimal("500.00"));
        ProductResponse expected = buildResponse(1L, "Galaxy S24", "Elektronik");

        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productMapper.toResponse(saved, "tr")).thenReturn(expected);

        ProductResponse result = productService.createProduct(request);

        assertThat(result).isEqualTo(expected);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void updateProduct_whenProductExists_updatesFieldsAndReturnsResponse() {
        Product existing = buildProduct(1L, "Elektronik", "Apple", new BigDecimal("1000.00"));
        UpdateProductRequest request = new UpdateProductRequest("Telefon", null, new BigDecimal("900.00"), null, null);
        ProductResponse expected = buildResponse(1L, "iPhone", "Telefon");

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);
        when(productMapper.toResponse(existing, "tr")).thenReturn(expected);

        ProductResponse result = productService.updateProduct(1L, request);

        assertThat(result.category()).isEqualTo("Telefon");
        verify(productRepository).save(existing);
    }

    @Test
    void updateProduct_whenProductNotFound_throwsProductNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateProductRequest request = new UpdateProductRequest(null, null, null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(99L, request))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProduct_whenProductExists_deletesSuccessfully() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_whenProductNotFound_throwsProductNotFoundException() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void listProducts_withCategoryFilter_returnsPagedResult() {
        Product product = buildProduct(1L, "Elektronik", "Apple", new BigDecimal("1000.00"));
        ProductResponse response = buildResponse(1L, "iPhone", "Elektronik");
        Page<Product> productPage = new PageImpl<>(List.of(product));
        Pageable pageable = PageRequest.of(0, 20);
        ProductFilterRequest filter = new ProductFilterRequest("Elektronik", null, null, null);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(productPage);
        when(productMapper.toResponse(product, "tr")).thenReturn(response);

        Page<ProductResponse> result = productService.listProducts(filter, "tr", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).category()).isEqualTo("Elektronik");
    }

    @Test
    void listProducts_withEmptyResult_returnsEmptyPage() {
        ProductFilterRequest filter = new ProductFilterRequest("Mevcut-Olmayan-Kategori", null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        Page<ProductResponse> result = productService.listProducts(filter, "tr", pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    private Product buildProduct(Long id, String category, String brand, BigDecimal price) {
        return Product.builder()
                .id(id)
                .category(category)
                .brand(brand)
                .basePrice(price)
                .active(true)
                .build();
    }

    private ProductResponse buildResponse(Long id, String name, String category) {
        return ProductResponse.builder()
                .id(id)
                .name(name)
                .category(category)
                .basePrice(new BigDecimal("1000.00"))
                .active(true)
                .build();
    }
}
