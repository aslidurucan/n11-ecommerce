package com.n11bootcamp.cart.controller;

import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(jwt.getSubject());
    }

    @PostMapping("/items")
    public CartResponse addItem(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody AddItemRequest request,
        @RequestHeader(value = "Accept-Language", defaultValue = "tr") String language
    ) {
        return cartService.addItem(jwt.getSubject(), request, language);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long productId,
        @Valid @RequestBody UpdateQuantityRequest request
    ) {
        return cartService.updateQuantity(jwt.getSubject(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long productId
    ) {
        cartService.removeItem(jwt.getSubject(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal Jwt jwt) {
        cartService.clearCart(jwt.getSubject());
    }
}
