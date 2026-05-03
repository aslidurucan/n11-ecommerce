package com.n11bootcamp.product.mapper;

import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.entity.Product;
import com.n11bootcamp.product.entity.ProductTranslation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product, String language) {
        ProductTranslation translation = resolveTranslation(product.getTranslations(), language);

        return ProductResponse.builder()
            .id(product.getId())
            .name(translation != null ? translation.getName() : null)
            .description(translation != null ? translation.getDescription() : null)
            .category(product.getCategory())
            .brand(product.getBrand())
            .basePrice(product.getBasePrice())
            .imageUrl(product.getImageUrl())
            .active(product.getActive())
            .createdAt(product.getCreatedAt())
            .build();
    }


    private ProductTranslation resolveTranslation(List<ProductTranslation> translations, String language) {
        if (translations == null || translations.isEmpty()) return null;

        return translations.stream()
            .filter(t -> t.getLanguage().equalsIgnoreCase(language))
            .findFirst()
            .orElseGet(() -> translations.stream()
                .filter(t -> "en".equalsIgnoreCase(t.getLanguage()))
                .findFirst()
                .orElse(translations.get(0)));
    }
}
