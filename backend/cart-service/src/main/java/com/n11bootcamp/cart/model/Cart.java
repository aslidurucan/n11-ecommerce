package com.n11bootcamp.cart.model;

import com.n11bootcamp.cart.exception.CartItemNotFoundException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    public static final int MAX_QUANTITY_PER_ITEM = 99;

    private String userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    private Instant updatedAt;

    public void addOrMergeItem(CartItem newItem) {
        validatePositiveQuantity(newItem.getQuantity());

        Optional<CartItem> existing = findItem(newItem.getProductId());
        if (existing.isPresent()) {
            int newTotal = existing.get().getQuantity() + newItem.getQuantity();
            validateMaxQuantity(newTotal, newItem.getProductId());
            existing.get().setQuantity(newTotal);
        } else {
            validateMaxQuantity(newItem.getQuantity(), newItem.getProductId());
            items.add(newItem);
        }
        this.updatedAt = Instant.now();
    }

    public void updateItemQuantity(Long productId, int quantity) {
        validatePositiveQuantity(quantity);
        validateMaxQuantity(quantity, productId);

        CartItem item = findItem(productId)
            .orElseThrow(() -> new CartItemNotFoundException(productId));
        item.setQuantity(quantity);
        this.updatedAt = Instant.now();
    }

    public void removeItem(Long productId) {
        boolean removed = items.removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            throw new CartItemNotFoundException(productId);
        }
        this.updatedAt = Instant.now();
    }

    public void clear() {
        items.clear();
        this.updatedAt = Instant.now();
    }

    public BigDecimal grandTotal() {
        return items.stream()
            .map(CartItem::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int itemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    private Optional<CartItem> findItem(Long productId) {
        return items.stream()
            .filter(i -> i.getProductId().equals(productId))
            .findFirst();
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "Quantity must be positive, got: " + quantity);
        }
    }

    private void validateMaxQuantity(int quantity, Long productId) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException(
                "Cannot add more than " + MAX_QUANTITY_PER_ITEM
                    + " of product " + productId + " (requested: " + quantity + ")");
        }
    }
}
