package com.n11bootcamp.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.cart.config.SecurityConfig;
import com.n11bootcamp.cart.dto.AddItemRequest;
import com.n11bootcamp.cart.dto.CartItemResponse;
import com.n11bootcamp.cart.dto.CartResponse;
import com.n11bootcamp.cart.dto.UpdateQuantityRequest;
import com.n11bootcamp.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getCart_whenAuthenticated_returns200WithCart() throws Exception {
        CartResponse response = buildCartResponse("user-1");
        when(cartService.getCart("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/cart")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.itemCount").value(2));
    }

    @Test
    void getCart_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addItem_whenValidRequest_returns200WithUpdatedCart() throws Exception {
        AddItemRequest request = new AddItemRequest(101L, 2);
        CartResponse response = buildCartResponse("user-1");

        when(cartService.addItem(eq("user-1"), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/cart/items")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(101));
    }

    @Test
    void addItem_whenProductIdMissing_returns400() throws Exception {
        AddItemRequest request = new AddItemRequest(null, 2);

        mockMvc.perform(post("/api/cart/items")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateQuantity_whenValidRequest_returns200() throws Exception {
        UpdateQuantityRequest request = new UpdateQuantityRequest(5);
        CartResponse response = buildCartResponse("user-1");

        when(cartService.updateQuantity(eq("user-1"), eq(101L), any())).thenReturn(response);

        mockMvc.perform(put("/api/cart/items/101")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void removeItem_whenAuthenticated_returns204() throws Exception {
        doNothing().when(cartService).removeItem("user-1", 101L);

        mockMvc.perform(delete("/api/cart/items/101")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());

        verify(cartService).removeItem("user-1", 101L);
    }

    @Test
    void clearCart_whenAuthenticated_returns204() throws Exception {
        doNothing().when(cartService).clearCart("user-1");

        mockMvc.perform(delete("/api/cart")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());

        verify(cartService).clearCart("user-1");
    }

    private CartResponse buildCartResponse(String userId) {
        CartItemResponse item = new CartItemResponse(
                101L, "iPhone", new BigDecimal("1000.00"), 2, new BigDecimal("2000.00")
        );
        return new CartResponse(userId, List.of(item), 2, new BigDecimal("2000.00"), Instant.now());
    }
}
