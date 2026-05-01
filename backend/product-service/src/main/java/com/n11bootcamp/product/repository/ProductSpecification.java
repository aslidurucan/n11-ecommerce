package com.n11bootcamp.product.repository;

import com.n11bootcamp.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Dinamik filtreleme için Specification factory.
 *
 * <p>Tüm string karşılaştırmaları case-insensitive — DB tarafında cb.lower(),
 * uygulama tarafında <code>toLowerCase(Locale.ENGLISH)</code>.</p>
 *
 * <p><b>Niye Locale.ENGLISH?</b> Türkçe locale'de "İSTANBUL".toLowerCase() Türkçe
 * dotted-i kuralları uygular ("i̇stanbul"). Bu DB'deki standart küçük harfle
 * eşleşmez. Locale.ENGLISH ile bu sorun yok — JVM locale'inden bağımsız,
 * portable davranış. User-service ve stock-service ile tutarlı.</p>
 */
public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(
            cb.lower(root.get("category")), category.toLowerCase(Locale.ENGLISH)
        );
    }

    public static Specification<Product> hasBrand(String brand) {
        return (root, query, cb) -> cb.equal(
            cb.lower(root.get("brand")), brand.toLowerCase(Locale.ENGLISH)
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
