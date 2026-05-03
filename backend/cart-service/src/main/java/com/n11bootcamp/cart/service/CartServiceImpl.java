package com.n11bootcamp.cart.service;

import com.n11bootcamp.cart.client.ProductClient;
import com.n11bootcamp.cart.client.ProductInfo;
import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartItemResponse;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.exception.ProductNotAvailableException;
import com.n11bootcamp.cart.model.Cart;
import com.n11bootcamp.cart.model.CartItem;
import com.n11bootcamp.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    @Override
    public CartResponse getCart(String userId) {
        return toResponse(loadOrCreate(userId));
    }

    @Override
    public CartResponse addItem(String userId, AddItemRequest request, String language) {
        ProductInfo product = productClient.getProduct(request.productId(), language);
        if (!product.active()) {
            throw new ProductNotAvailableException(request.productId());
        }
        Cart cart = loadOrCreate(userId);
        cart.addOrMergeItem(CartItem.builder()
            .productId(product.id())
            .productName(product.name())
            .unitPrice(product.basePrice())
            .quantity(request.quantity())
            .build());
        cartRepository.save(cart);
        log.debug("Item added to cart: userId={}, productId={}", userId, product.id());
        return toResponse(cart);
    }

    @Override
    public CartResponse updateQuantity(String userId, Long productId, UpdateQuantityRequest request) {
        Cart cart = loadOrCreate(userId);
        cart.updateItemQuantity(productId, request.quantity());
        cartRepository.save(cart);
        return toResponse(cart);
    }

    @Override
    public void removeItem(String userId, Long productId) {
        Cart cart = loadOrCreate(userId);
        cart.removeItem(productId);
        cartRepository.save(cart);
    }

    @Override
    public void clearCart(String userId) {
        cartRepository.deleteByUserId(userId);
    }

    private Cart loadOrCreate(String userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> Cart.builder()
                .userId(userId)
                .updatedAt(Instant.now())
                .build());
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
            .map(i -> new CartItemResponse(
                i.getProductId(),
                i.getProductName(),
                i.getUnitPrice(),
                i.getQuantity(),
                i.totalPrice()
            ))
            .toList();
        return new CartResponse(
            cart.getUserId(),
            items,
            cart.itemCount(),
            cart.grandTotal(),
            cart.getUpdatedAt()
        );
    }
}
