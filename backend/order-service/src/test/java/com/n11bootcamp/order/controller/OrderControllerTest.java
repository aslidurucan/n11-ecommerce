package com.n11bootcamp.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.order.config.SecurityConfig;
import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.dto.ShippingAddressRequest;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createOrder_whenValidRequest_returns201() throws Exception {
        CreateOrderRequest request = buildCreateOrderRequest();
        OrderResponse response = buildOrderResponse(1L);

        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .header("X-User-Id", "user-1")
                        .header("Idempotency-Key", "test-key-abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createOrder_whenMissingIdempotencyKey_returns400() throws Exception {
        CreateOrderRequest request = buildCreateOrderRequest();

        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_whenMissingUserIdHeader_returns400() throws Exception {
        CreateOrderRequest request = buildCreateOrderRequest();

        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_whenInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrder_whenAuthenticated_returns200() throws Exception {
        OrderResponse response = buildOrderResponse(1L);
        when(orderService.getOrder(1L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void getOrder_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void myOrders_whenAuthenticated_returns200() throws Exception {
        when(orderService.findUserOrders(eq("user-1"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/orders/me")
                        .with(jwt())
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
    }

    @Test
    void allOrders_whenAdmin_returns200() throws Exception {
        when(orderService.findAllOrders(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void allOrders_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allOrders_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    private CreateOrderRequest buildCreateOrderRequest() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(101L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setProductName("iPhone");

        ShippingAddressRequest address = new ShippingAddressRequest();
        address.setFirstName("Asli");
        address.setLastName("Durucan");
        address.setEmail("asli@example.com");
        address.setPhone("05001234567");
        address.setAddress("Test Street 1");
        address.setCity("Istanbul");
        address.setCountry("TR");

        CardRequest card = new CardRequest();
        card.setHolderName("ASLI DURUCAN");
        card.setNumber("5528790000000008");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        request.setShippingAddress(address);
        request.setCard(card);

        return request;
    }

    private OrderResponse buildOrderResponse(Long id) {
        return OrderResponse.builder()
                .id(id)
                .userId("user-1")
                .idempotencyKey("idem-key-" + id)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .currency("TRY")
                .createdAt(Instant.now())
                .items(List.of())
                .build();
    }
}