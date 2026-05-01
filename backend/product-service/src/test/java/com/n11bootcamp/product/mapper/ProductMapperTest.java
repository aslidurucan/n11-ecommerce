package com.n11bootcamp.product.mapper;

import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.entity.Product;
import com.n11bootcamp.product.entity.ProductTranslation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private ProductMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProductMapper();
    }

    @Test
    void toResponse_whenMatchingLanguageExists_returnsCorrectTranslation() {
        Product product = buildProductWithTranslations(List.of(
                buildTranslation("tr", "iPhone 15", "Apple telefon"),
                buildTranslation("en", "iPhone 15", "Apple phone")
        ));

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.name()).isEqualTo("iPhone 15");
        assertThat(result.description()).isEqualTo("Apple telefon");
    }

    @Test
    void toResponse_whenRequestedLanguageNotFound_fallsBackToEnglish() {
        Product product = buildProductWithTranslations(List.of(
                buildTranslation("en", "iPhone 15", "Apple phone"),
                buildTranslation("de", "iPhone 15", "Apple Handy")
        ));

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.description()).isEqualTo("Apple phone");
    }

    @Test
    void toResponse_whenNeitherRequestedNorEnglishExists_returnsFirstTranslation() {
        Product product = buildProductWithTranslations(List.of(
                buildTranslation("fr", "iPhone 15", "Téléphone Apple"),
                buildTranslation("de", "iPhone 15", "Apple Handy")
        ));

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.description()).isEqualTo("Téléphone Apple");
    }

    @Test
    void toResponse_whenNoTranslations_returnsNullNameAndDescription() {
        Product product = buildProductWithTranslations(List.of());

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.name()).isNull();
        assertThat(result.description()).isNull();
    }

    @Test
    void toResponse_alwaysMapsProductFields() {
        Product product = buildProductWithTranslations(List.of(
                buildTranslation("tr", "iPhone 15", "Telefon")
        ));

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.category()).isEqualTo("Elektronik");
        assertThat(result.brand()).isEqualTo("Apple");
        assertThat(result.basePrice()).isEqualByComparingTo("1000.00");
        assertThat(result.active()).isTrue();
    }

    @Test
    void toResponse_isCaseInsensitiveForLanguage() {
        Product product = buildProductWithTranslations(List.of(
                buildTranslation("TR", "iPhone 15", "Büyük harf dil kodu")
        ));

        ProductResponse result = mapper.toResponse(product, "tr");

        assertThat(result.description()).isEqualTo("Büyük harf dil kodu");
    }

    private Product buildProductWithTranslations(List<ProductTranslation> translations) {
        Product product = Product.builder()
                .id(1L)
                .category("Elektronik")
                .brand("Apple")
                .basePrice(new BigDecimal("1000.00"))
                .active(true)
                .build();
        translations.forEach(product::addTranslation);
        return product;
    }

    private ProductTranslation buildTranslation(String lang, String name, String description) {
        return ProductTranslation.builder()
                .language(lang)
                .name(name)
                .description(description)
                .build();
    }
}
