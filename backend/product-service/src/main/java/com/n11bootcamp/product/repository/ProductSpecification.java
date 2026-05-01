package com.n11bootcamp.product.repository;

import com.n11bootcamp.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(
            cb.lower(root.get("category")), category.toLowerCase()
        );
    }

    public static Specification<Product> hasBrand(String brand) {
        return (root, query, cb) -> cb.equal(
            cb.lower(root.get("brand")), brand.toLowerCase()
        );
    }

    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> cb.between(root.get("basePrice"), min, max);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("basePrice"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("basePrice"), max);
    }
}
