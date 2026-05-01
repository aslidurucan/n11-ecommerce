package com.n11bootcamp.product.controller;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import com.n11bootcamp.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Ürün kataloğu")
public class ProductController {

    private static final String DEFAULT_LANG = "tr";

    private final ProductService productService;

    @GetMapping("/{id}")
    @Operation(summary = "Ürün detayı")
    public ResponseEntity<ProductResponse> getProduct(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = DEFAULT_LANG) String language) {
        return ResponseEntity.ok(productService.getProduct(id, language));
    }

    @GetMapping
    @Operation(summary = "Ürün listesi — filtreli ve sayfalı")
    public ResponseEntity<Page<ProductResponse>> listProducts(
            @RequestHeader(value = "Accept-Language", defaultValue = DEFAULT_LANG) String language,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        ProductFilterRequest filter = new ProductFilterRequest(category, brand, minPrice, maxPrice);
        return ResponseEntity.ok(productService.listProducts(filter, language, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Ürün ekle — ADMIN")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Ürün kısmen güncelle — ADMIN")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Ürün sil — ADMIN")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
