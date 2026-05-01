package com.n11bootcamp.cart.service;

import com.n11bootcamp.cart.client.ProductClient;
import com.n11bootcamp.cart.client.ProductInfo;
import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import com.n11bootcamp.cart.model.Cart;
import com.n11bootcamp.cart.model.CartItem;
import com.n11bootcamp.cart.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void getCart_whenCartExists_returnsCartWithItems() {
        Cart cart = buildCart("user-1", 101L, new BigDecimal("1000.00"), 2);
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(cart));

        CartResponse result = cartService.getCart("user-1");

        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.items()).hasSize(1);
        assertThat(result.itemCount()).isEqualTo(2);
    }

    @Test
    void getCart_whenCartDoesNotExist_returnsEmptyCart() {
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        CartResponse result = cartService.getCart("user-1");

        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.items()).isEmpty();
        assertThat(result.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void addItem_whenProductActive_addsItemAndSavesCart() {
        ProductInfo product = new ProductInfo(101L, "iPhone", new BigDecimal("1000.00"), true);
        AddItemRequest request = new AddItemRequest(101L, 2);

        when(productClient.getProduct(101L, "tr")).thenReturn(product);
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        CartResponse result = cartService.addItem("user-1", request, "tr");

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productId()).isEqualTo(101L);
        assertThat(result.itemCount()).isEqualTo(2);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addItem_whenProductNotActive_throwsProductNotAvailableException() {
        ProductInfo inactiveProduct = new ProductInfo(101L, "iPhone", new BigDecimal("1000.00"), false);
        AddItemRequest request = new AddItemRequest(101L, 1);

        when(productClient.getProduct(101L, "tr")).thenReturn(inactiveProduct);

        assertThatThrownBy(() -> cartService.addItem("user-1", request, "tr"))
                .isInstanceOf(ProductNotAvailableException.class);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_whenProductAlreadyInCart_mergesQuantity() {
        Cart existingCart = buildCart("user-1", 101L, new BigDecimal("1000.00"), 1);
        ProductInfo product = new ProductInfo(101L, "iPhone", new BigDecimal("1000.00"), true);
        AddItemRequest request = new AddItemRequest(101L, 3);

        when(productClient.getProduct(101L, "tr")).thenReturn(product);
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(existingCart));

        CartResponse result = cartService.addItem("user-1", request, "tr");

        assertThat(result.items()).hasSize(1);
        assertThat(result.itemCount()).isEqualTo(4);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void updateQuantity_whenItemExists_updatesAndSaves() {
        Cart cart = buildCart("user-1", 101L, new BigDecimal("1000.00"), 2);
        UpdateQuantityRequest request = new UpdateQuantityRequest(5);

        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(cart));

        CartResponse result = cartService.updateQuantity("user-1", 101L, request);

        assertThat(result.itemCount()).isEqualTo(5);
        verify(cartRepository).save(cart);
    }

    @Test
    void removeItem_whenItemExists_removesAndSaves() {
        Cart cart = buildCart("user-1", 101L, new BigDecimal("1000.00"), 2);
        when(cartRepository.findByUserId("user-1")).thenReturn(Optional.of(cart));

        cartService.removeItem("user-1", 101L);

        verify(cartRepository).save(cart);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void clearCart_callsDeleteByUserId() {
        cartService.clearCart("user-1");

        verify(cartRepository).deleteByUserId("user-1");
    }

    private Cart buildCart(String userId, Long productId, BigDecimal price, int quantity) {
        Cart cart = Cart.builder()
                .userId(userId)
                .updatedAt(Instant.now())
                .build();
        cart.addOrMergeItem(CartItem.builder()
                .productId(productId)
                .productName("Test Ürün")
                .unitPrice(price)
                .quantity(quantity)
                .build());
        return cart;
    }
}
