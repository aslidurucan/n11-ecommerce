package com.n11bootcamp.cart.service;

import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;

public interface CartService {

    CartResponse getCart(String userId);

    CartResponse addItem(String userId, AddItemRequest request, String language);

    CartResponse updateQuantity(String userId, Long productId, UpdateQuantityRequest request);

    void removeItem(String userId, Long productId);

    void clearCart(String userId);
}
