package com.n11bootcamp.product.integration;

import com.n11bootcamp.product.dto.CreateProductRequest;
import com.n11bootcamp.product.dto.ProductFilterRequest;
import com.n11bootcamp.product.dto.ProductResponse;
import com.n11bootcamp.product.dto.UpdateProductRequest;
import com.n11bootcamp.product.exception.ProductNotFoundException;
import com.n11bootcamp.product.repository.ProductRepository;
import com.n11bootcamp.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Product Service — Entegrasyon Testi
 *
 * <p>Gerçek bir PostgreSQL container başlatır ve Flyway migration'larını çalıştırır.
 * Redis cache devre dışı bırakılmıştır; yalnızca JPA/DB katmanı test edilmektedir.</p>
 *
 * <p>Her test metodu {@code @Transactional} rollback ile izole çalışır — veritabanı
 * state'i testler arasında sıfırlanır.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("ProductService — Entegrasyon Testleri (gerçek PostgreSQL)")
class ProductServiceIntegrationTest {

    // =========================================================
    // Güvenlik bypass: Keycloak bağlantısı olmadan JwtDecoder
    // =========================================================
    @TestConfiguration
    static class SecurityOverride {
        /**
         * @Primary → Spring Security autoconfigure'ın oluşturduğu JwtDecoder'ı
         * devre dışı bırakır. Testlerde Keycloak bağlantısı gerekmez.
         */
        @Bean
        @Primary
        JwtDecoder mockJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user-id")
                    .claim("preferred_username", "testuser")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    // =========================================================
    // PostgreSQL Testcontainer — tüm testlerde paylaşılır
    // =========================================================
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("productdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    // =========================================================
    // CREATE
    // =========================================================

    @Test
    @DisplayName("Ürün oluşturulunca DB'ye persist edilir ve id döner")
    void createProduct_persistsToDatabase() {
        CreateProductRequest request = buildCreateRequest(
                "Elektronik", "Apple", new BigDecimal("29999.00"),
                "tr", "iPhone 16 Pro", "Titanium gövde, A18 Pro çip"
        );

        ProductResponse response = productService.createProduct(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.category()).isEqualTo("Elektronik");
        assertThat(response.brand()).isEqualTo("Apple");
        assertThat(response.basePrice()).isEqualByComparingTo("29999.00");
        assertThat(response.active()).isTrue();

        // DB'de gerçekten var mı?
        assertThat(productRepository.findById(response.id())).isPresent();
    }

    @Test
    @DisplayName("Farklı kategoride 2 ürün oluşturunca DB'de 2 kayıt oluşur")
    void createProduct_multipleTimes_allPersistedSeparately() {
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Samsung", new BigDecimal("15000.00"),
                "tr", "Galaxy S24", "Yapay zeka özellikli akıllı telefon"
        ));
        productService.createProduct(buildCreateRequest(
                "Giyim", "Nike", new BigDecimal("2500.00"),
                "tr", "Air Max 90", "Spor ayakkabı"
        ));

        assertThat(productRepository.count()).isEqualTo(2);
    }

    // =========================================================
    // GET
    // =========================================================

    @Test
    @DisplayName("Mevcut ürün getirilince doğru veriler döner")
    void getProduct_whenExists_returnsCorrectData() {
        ProductResponse created = productService.createProduct(
                buildCreateRequest("Beyaz Eşya", "Arçelik",
                        new BigDecimal("8500.00"), "tr", "Bulaşık Makinesi", "A+++ enerji")
        );

        ProductResponse fetched = productService.getProduct(created.id(), "tr");

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.brand()).isEqualTo("Arçelik");
        assertThat(fetched.category()).isEqualTo("Beyaz Eşya");
    }

    @Test
    @DisplayName("Mevcut olmayan ürün getirilince ProductNotFoundException fırlar")
    void getProduct_whenNotExists_throwsProductNotFoundException() {
        assertThatThrownBy(() -> productService.getProduct(999_999L, "tr"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // =========================================================
    // LIST / FILTER
    // =========================================================

    @Test
    @DisplayName("Kategori filtresi uygulandığında yalnızca o kategori döner")
    void listProducts_withCategoryFilter_returnsOnlyMatchingCategory() {
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Sony", new BigDecimal("3500.00"),
                "tr", "WH-1000XM5", "Kablosuz kulaklık"));
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Apple", new BigDecimal("5500.00"),
                "tr", "AirPods Pro 2", "Aktif gürültü engelleme"));
        productService.createProduct(buildCreateRequest(
                "Kitap", "Yapı Kredi", new BigDecimal("150.00"),
                "tr", "Sefiller", "Victor Hugo"));

        ProductFilterRequest filter = new ProductFilterRequest("Elektronik", null, null, null);
        Page<ProductResponse> result = productService.listProducts(
                filter, "tr", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allSatisfy(p -> assertThat(p.category()).isEqualTo("Elektronik"));
    }

    @Test
    @DisplayName("Fiyat aralığı filtresi uygulandığında doğru ürünler döner")
    void listProducts_withPriceFilter_returnsProductsInRange() {
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Xiaomi", new BigDecimal("3000.00"),
                "tr", "Redmi 13C", "Bütçe dostu telefon"));
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Apple", new BigDecimal("45000.00"),
                "tr", "iPhone 16 Pro Max", "Flagship"));
        productService.createProduct(buildCreateRequest(
                "Elektronik", "Samsung", new BigDecimal("12000.00"),
                "tr", "Galaxy A55", "Orta segment"));

        // 1000 - 20000 TL arası filtresi
        ProductFilterRequest filter = new ProductFilterRequest(
                null, null, new BigDecimal("1000"), new BigDecimal("20000"));
        Page<ProductResponse> result = productService.listProducts(
                filter, "tr", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        result.getContent().forEach(p ->
                assertThat(p.basePrice()).isBetween(new BigDecimal("1000"), new BigDecimal("20000"))
        );
    }

    // =========================================================
    // UPDATE
    // =========================================================

    @Test
    @DisplayName("Ürün güncellenince değişiklikler DB'ye yansır")
    void updateProduct_persistsChangesToDatabase() {
        ProductResponse created = productService.createProduct(
                buildCreateRequest("Elektronik", "LG",
                        new BigDecimal("12000.00"), "tr", "OLED TV 55\"", "4K OLED"));

        UpdateProductRequest updateRequest = new UpdateProductRequest(
                "Televizyon", null, new BigDecimal("10500.00"), null, null
        );
        productService.updateProduct(created.id(), updateRequest);

        // Flush + re-fetch from DB (same transaction — EntityManager has the updated entity)
        ProductResponse updated = productService.getProduct(created.id(), "tr");

        assertThat(updated.category()).isEqualTo("Televizyon");
        assertThat(updated.basePrice()).isEqualByComparingTo("10500.00");
        assertThat(updated.brand()).isEqualTo("LG"); // değişmemeli
    }

    // =========================================================
    // DELETE
    // =========================================================

    @Test
    @DisplayName("Ürün silinince DB'den kalkar")
    void deleteProduct_removesFromDatabase() {
        ProductResponse created = productService.createProduct(
                buildCreateRequest("Elektronik", "JBL",
                        new BigDecimal("1800.00"), "tr", "Charge 5", "Taşınabilir hoparlör"));

        assertThat(productRepository.findById(created.id())).isPresent();

        productService.deleteProduct(created.id());

        assertThat(productRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("Olmayan ürün silinmeye çalışılınca ProductNotFoundException fırlar")
    void deleteProduct_whenNotExists_throwsException() {
        assertThatThrownBy(() -> productService.deleteProduct(999_999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // =========================================================
    // YARDIMCI METODLAR
    // =========================================================

    private CreateProductRequest buildCreateRequest(String category, String brand,
                                                    BigDecimal price, String lang,
                                                    String name, String description) {
        return new CreateProductRequest(
                category, brand, price, "http://example.com/img.jpg",
                List.of(new CreateProductRequest.TranslationRequest(lang, name, description))
        );
    }
}
