package com.n11bootcamp.cart.model;

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

    private String userId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    private Instant updatedAt;

    public void addOrMergeItem(CartItem newItem) {
        Optional<CartItem> existing = findItem(newItem.getProductId());
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + newItem.getQuantity());
        } else {
            items.add(newItem);
        }
        this.updatedAt = Instant.now();
    }

    public void updateItemQuantity(Long productId, int quantity) {
        CartItem item = findItem(productId)
            .orElseThrow(() -> new com.n11bootcamp.cart.exception.CartItemNotFoundException(productId));
        item.setQuantity(quantity);
        this.updatedAt = Instant.now();
    }

    public void removeItem(Long productId) {
        boolean removed = items.removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            throw new com.n11bootcamp.cart.exception.CartItemNotFoundException(productId);
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
}
