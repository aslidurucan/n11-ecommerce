package com.n11bootcamp.cart.integration;

import com.n11bootcamp.cart.client.ProductClient;
import com.n11bootcamp.cart.client.ProductInfo;
import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import com.n11bootcamp.cart.repository.CartRepository;
import com.n11bootcamp.cart.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CartService — Redis Entegrasyon Testleri")
class CartServiceIntegrationTest {

    @TestConfiguration
    static class SecurityOverride {
        @Bean @Primary
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

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean
    private ProductClient productClient;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    private static final String USER_1 = "user-redis-1";
    private static final String USER_2 = "user-redis-2";
    private static final Long PRODUCT_IPHONE = 101L;
    private static final Long PRODUCT_SAMSUNG = 102L;

    @BeforeEach
    void setupProductClientMock() {
        when(productClient.getProduct(PRODUCT_IPHONE, "tr"))
                .thenReturn(new ProductInfo(PRODUCT_IPHONE, "iPhone 16", new BigDecimal("29999.00"), true));
        when(productClient.getProduct(PRODUCT_SAMSUNG, "tr"))
                .thenReturn(new ProductInfo(PRODUCT_SAMSUNG, "Galaxy S24", new BigDecimal("18000.00"), true));
    }

    @AfterEach
    void cleanRedis() {
        cartRepository.deleteByUserId(USER_1);
        cartRepository.deleteByUserId(USER_2);
    }

    @Test
    @DisplayName("Daha önce sepet oluşturulmamış kullanıcı için boş sepet döner")
    void getCart_whenNoCart_returnsEmptyCart() {
        CartResponse response = cartService.getCart(USER_1);

        assertThat(response.userId()).isEqualTo(USER_1);
        assertThat(response.items()).isEmpty();
        assertThat(response.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.itemCount()).isZero();
    }

    @Test
    @DisplayName("Ürün eklenince sepet Redis'e persist edilir")
    void addItem_persistsCartInRedis() {
        AddItemRequest request = new AddItemRequest(PRODUCT_IPHONE, 2);

        CartResponse response = cartService.addItem(USER_1, request, "tr");

        assertThat(response.userId()).isEqualTo(USER_1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(PRODUCT_IPHONE);
        assertThat(response.items().get(0).productName()).isEqualTo("iPhone 16");
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.grandTotal()).isEqualByComparingTo("59998.00");

        CartResponse fromRedis = cartService.getCart(USER_1);
        assertThat(fromRedis.items()).hasSize(1);
        assertThat(fromRedis.itemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Aynı ürün iki kez eklenince miktarlar birleşir (merge)")
    void addItem_sameProductTwice_quantitiesMerged() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 1), "tr");
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 3), "tr");

        CartResponse response = cartService.getCart(USER_1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.itemCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Farklı ürünler eklenince her biri ayrı item olarak görünür")
    void addItem_differentProducts_shownAsSeparateItems() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 1), "tr");
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_SAMSUNG, 2), "tr");

        CartResponse response = cartService.getCart(USER_1);

        assertThat(response.items()).hasSize(2);
        assertThat(response.itemCount()).isEqualTo(3);
        assertThat(response.grandTotal())
                .isEqualByComparingTo("65999.00");
    }

    @Test
    @DisplayName("Pasif ürün eklenince ProductNotAvailableException fırlar, sepet değişmez")
    void addItem_inactiveProduct_throwsProductNotAvailableException() {
        Long inactiveProductId = 999L;
        when(productClient.getProduct(inactiveProductId, "tr"))
                .thenReturn(new ProductInfo(inactiveProductId, "Pasif Ürün",
                        new BigDecimal("100.00"), false));

        assertThatThrownBy(() ->
                cartService.addItem(USER_1, new AddItemRequest(inactiveProductId, 1), "tr"))
                .isInstanceOf(ProductNotAvailableException.class);

        assertThat(cartService.getCart(USER_1).items()).isEmpty();
    }

    @Test
    @DisplayName("Miktar güncellenince Redis'teki değer değişir")
    void updateQuantity_updatesInRedis() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 2), "tr");

        CartResponse updated = cartService.updateQuantity(USER_1, PRODUCT_IPHONE,
                new UpdateQuantityRequest(5));

        assertThat(updated.itemCount()).isEqualTo(5);

        CartResponse fromRedis = cartService.getCart(USER_1);
        assertThat(fromRedis.itemCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Ürün çıkarılınca sepetten kalkar, Redis güncellenir")
    void removeItem_removedFromCartInRedis() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 1), "tr");
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_SAMSUNG, 2), "tr");

        cartService.removeItem(USER_1, PRODUCT_IPHONE);

        CartResponse response = cartService.getCart(USER_1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(PRODUCT_SAMSUNG);
    }

    @Test
    @DisplayName("Sepet temizlenince Redis'ten tamamen silinir")
    void clearCart_removedFromRedis() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 3), "tr");
        assertThat(cartService.getCart(USER_1).items()).hasSize(1);

        cartService.clearCart(USER_1);

        CartResponse afterClear = cartService.getCart(USER_1);
        assertThat(afterClear.items()).isEmpty();
        assertThat(afterClear.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Farklı kullanıcıların sepetleri birbirini etkilemez")
    void carts_differentUsers_completelyIsolated() {
        cartService.addItem(USER_1, new AddItemRequest(PRODUCT_IPHONE, 2), "tr");
        cartService.addItem(USER_2, new AddItemRequest(PRODUCT_SAMSUNG, 1), "tr");

        CartResponse user1Cart = cartService.getCart(USER_1);
        CartResponse user2Cart = cartService.getCart(USER_2);

        assertThat(user1Cart.items()).hasSize(1);
        assertThat(user1Cart.items().get(0).productId()).isEqualTo(PRODUCT_IPHONE);

        assertThat(user2Cart.items()).hasSize(1);
        assertThat(user2Cart.items().get(0).productId()).isEqualTo(PRODUCT_SAMSUNG);

        cartService.clearCart(USER_1);
        assertThat(cartService.getCart(USER_2).items()).hasSize(1);
    }
}
