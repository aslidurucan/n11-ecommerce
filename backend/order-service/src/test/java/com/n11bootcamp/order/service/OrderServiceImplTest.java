package com.n11bootcamp.order.service;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.dto.ShippingAddressRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.exception.OrderNotFoundException;
import com.n11bootcamp.order.mapper.OrderMapper;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.service.payment.CardCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private CardCacheService cardCacheService;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void injectRoutingKey() {
        ReflectionTestUtils.setField(orderService, "stockReserveRequestedRoutingKey",
                "order.stock.reserve.requested");
    }

    @Test
    void createOrder_whenNew_savesOrderAndPersistsOutboxEvent() {
        CreateOrderRequest request = buildCreateOrderRequest();
        Order savedOrder = buildOrder(1L, "user-1", OrderStatus.PENDING);

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toOrderItems(any())).thenReturn(List.of());
        when(orderMapper.toResponse(savedOrder)).thenReturn(buildOrderResponse(1L));

        OrderResponse result = orderService.createOrder("user-1", "testuser", "idem-key-1", request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(cardCacheService).storeCard(eq(1L), any(CardRequest.class));
        verify(outboxEventService).persist(
                eq("Order"), eq("1"), eq("OrderCreatedEvent"),
                eq("order.stock.reserve.requested"), any()
        );
    }

    @Test
    void createOrder_whenIdempotentKeyExists_returnsExistingOrderWithoutSaving() {
        CreateOrderRequest request = buildCreateOrderRequest();
        Order existing = buildOrder(42L, "user-1", OrderStatus.PENDING);

        when(orderRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));
        when(orderMapper.toResponse(existing)).thenReturn(buildOrderResponse(42L));

        OrderResponse result = orderService.createOrder("user-1", "testuser", "idem-key-1", request);

        assertThat(result.getId()).isEqualTo(42L);
        verify(orderRepository, never()).save(any());
        verify(outboxEventService, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    void createOrder_whenTotalIsZero_throwsIllegalArgumentException() {
        CreateOrderRequest request = buildCreateOrderRequest();
        request.getItems().get(0).setUnitPrice(BigDecimal.ZERO);

        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(
                "user-1", "testuser", "valid-key-for-test", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_whenExists_returnsResponse() {
        Order order = buildOrder(1L, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(buildOrderResponse(1L));

        OrderResponse result = orderService.getOrder(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getOrder_whenNotFound_throwsOrderNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void findUserOrders_delegatesToRepository() {
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(Page.empty());

        Page<OrderResponse> result = orderService.findUserOrders("user-1", Pageable.unpaged());

        assertThat(result).isEmpty();
    }

    @Test
    void updateOrderStatus_whenNotTerminal_updatesAndSaves() {
        Order order = buildOrder(1L, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(1L, OrderStatus.STOCK_RESERVED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_whenTerminal_doesNotSaveOrDeleteCard() {
        Order order = buildOrder(1L, "user-1", OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(1L, "already done");

        verify(orderRepository, never()).save(any());
        verify(cardCacheService, never()).deleteCard(any());
    }

    @Test
    void cancelOrder_whenNotTerminal_cancelsAndDeletesCard() {
        Order order = buildOrder(1L, "user-1", OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(1L, "payment failed");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentFailureReason()).isEqualTo("payment failed");
        verify(orderRepository).save(order);
        verify(cardCacheService).deleteCard(1L);
    }

    @Test
    void completeOrderPayment_setsCompletedAndDeletesCard() {
        Order order = buildOrder(1L, "user-1", OrderStatus.PAYMENT_PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.completeOrderPayment(1L, "pay-xyz");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentId()).isEqualTo("pay-xyz");
        verify(orderRepository).save(order);
        verify(cardCacheService).deleteCard(1L);
    }

    private Order buildOrder(Long id, String userId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .userId(userId)
                .username("testuser")
                .idempotencyKey("idem-key-" + id)
                .status(status)
                .totalAmount(new BigDecimal("100.00"))
                .currency("TRY")
                .build();
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
