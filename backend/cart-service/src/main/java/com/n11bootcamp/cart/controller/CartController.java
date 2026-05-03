package com.n11bootcamp.cart.controller;

import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Kullanıcı sepeti yönetimi")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Sepeti getir — kendi sepetin")
    public CartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return cartService.getCart(jwt.getSubject());
    }

    @PostMapping("/items")
    @Operation(summary = "Sepete ürün ekle")
    public CartResponse addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddItemRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "tr") String language
    ) {
        return cartService.addItem(jwt.getSubject(), request, language);
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Sepetteki ürün adedini güncelle")
    public CartResponse updateQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateQuantityRequest request
    ) {
        return cartService.updateQuantity(jwt.getSubject(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sepetten ürün çıkar")
    public void removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId
    ) {
        cartService.removeItem(jwt.getSubject(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sepeti tamamen boşalt")
    public void clearCart(@AuthenticationPrincipal Jwt jwt) {
        cartService.clearCart(jwt.getSubject());
    }
}