package com.n11bootcamp.cart.model;

import com.n11bootcamp.cart.exception.CartItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = Cart.builder().userId("user-1").build();
    }

    @Test
    void addOrMergeItem_whenNewProduct_addsToList() {
        CartItem item = buildItem(101L, "iPhone", new BigDecimal("1000.00"), 1);

        cart.addOrMergeItem(item);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductId()).isEqualTo(101L);
    }

    @Test
    void addOrMergeItem_whenSameProductAddedAgain_incrementsQuantity() {
        CartItem first = buildItem(101L, "iPhone", new BigDecimal("1000.00"), 2);
        CartItem second = buildItem(101L, "iPhone", new BigDecimal("1000.00"), 3);

        cart.addOrMergeItem(first);
        cart.addOrMergeItem(second);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void addOrMergeItem_whenDifferentProducts_addsBoth() {
        cart.addOrMergeItem(buildItem(101L, "iPhone", new BigDecimal("1000.00"), 1));
        cart.addOrMergeItem(buildItem(202L, "Samsung", new BigDecimal("800.00"), 2));

        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    void updateItemQuantity_whenItemExists_updatesQuantity() {
        cart.addOrMergeItem(buildItem(101L, "iPhone", new BigDecimal("1000.00"), 1));

        cart.updateItemQuantity(101L, 5);

        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void updateItemQuantity_whenItemNotFound_throwsCartItemNotFoundException() {
        assertThatThrownBy(() -> cart.updateItemQuantity(999L, 3))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void removeItem_whenItemExists_removesFromList() {
        cart.addOrMergeItem(buildItem(101L, "iPhone", new BigDecimal("1000.00"), 1));
        cart.addOrMergeItem(buildItem(202L, "Samsung", new BigDecimal("800.00"), 1));

        cart.removeItem(101L);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getProductId()).isEqualTo(202L);
    }

    @Test
    void removeItem_whenItemNotFound_throwsCartItemNotFoundException() {
        assertThatThrownBy(() -> cart.removeItem(999L))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void grandTotal_isCorrectlyCalculated() {
        cart.addOrMergeItem(buildItem(101L, "iPhone", new BigDecimal("1000.00"), 2));
        cart.addOrMergeItem(buildItem(202L, "Samsung", new BigDecimal("500.00"), 3));

        BigDecimal total = cart.grandTotal();

        assertThat(total).isEqualByComparingTo(new BigDecimal("3500.00"));
    }

    @Test
    void grandTotal_whenCartEmpty_returnsZero() {
        assertThat(cart.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void itemCount_isCorrectlyCalculated() {
        cart.addOrMergeItem(buildItem(101L, "iPhone", new BigDecimal("1000.00"), 2));
        cart.addOrMergeItem(buildItem(202L, "Samsung", new BigDecimal("500.00"), 3));

        assertThat(cart.itemCount()).isEqualTo(5);
    }

    private CartItem buildItem(Long productId, String name, BigDecimal price, int quantity) {
        return CartItem.builder()
                .productId(productId)
                .productName(name)
                .unitPrice(price)
                .quantity(quantity)
                .build();
    }
}
