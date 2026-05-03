package com.n11bootcamp.order.service;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.dto.ShippingAddressRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderItem;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.mapper.OrderMapper;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.service.payment.CardCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl — idempotency davranışı")
class OrderServiceIdempotencyTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventService outboxEventService;
    @Mock private CardCacheService cardCacheService;
    @Mock private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final String USER_ID = "user-123";
    private static final String USERNAME = "testuser";
    private static final String IDEMPOTENCY_KEY = "key-abc-123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                orderService, "stockReserveRequestedRoutingKey", "stock.reserve.requested");
    }

    @Test
    @DisplayName("Yeni idempotency key gelirse: yeni order yaratılır ve event yazılır")
    void createOrder_whenNewKey_createsNewOrderAndPersistsEvent() {
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        Order savedOrder = buildOrder(1L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toOrderItems(any())).thenReturn(List.of(buildOrderItem()));
        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(buildResponse(1L));


        OrderResponse response = orderService.createOrder(
                USER_ID, USERNAME, IDEMPOTENCY_KEY, buildRequest());


        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);


        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cardCacheService, times(1)).storeCard(any(Long.class), any(CardRequest.class));
        verify(outboxEventService, times(1)).persist(
                anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Aynı idempotency key tekrar gelirse: mevcut order döner, hiçbir yan etki yok")
    void createOrder_whenSameKeyReused_returnsExistingOrderWithoutSideEffects() {
        Order existingOrder = buildOrder(42L);
        when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existingOrder));
        when(orderMapper.toResponse(existingOrder))
                .thenReturn(buildResponse(42L));


        OrderResponse response = orderService.createOrder(
                USER_ID, USERNAME, IDEMPOTENCY_KEY, buildRequest());

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(42L);

        verify(orderRepository, never()).save(any(Order.class));
        verify(cardCacheService, never()).storeCard(any(), any());
        verify(outboxEventService, never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Idempotency key null gelirse: IllegalArgumentException fırlar")
    void createOrder_whenKeyIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> orderService.createOrder(
                USER_ID, USERNAME, null, buildRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        verify(orderRepository, never()).findByIdempotencyKey(anyString());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Idempotency key blank (boşluk) gelirse: IllegalArgumentException fırlar")
    void createOrder_whenKeyIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> orderService.createOrder(
                USER_ID, USERNAME, "   ", buildRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        verify(orderRepository, never()).findByIdempotencyKey(anyString());
        verify(orderRepository, never()).save(any(Order.class));
    }


    private CreateOrderRequest buildRequest() {
        CreateOrderRequest req = new CreateOrderRequest();

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("99.90"));
        item.setProductName("Test Product");
        req.setItems(List.of(item));

        ShippingAddressRequest addr = new ShippingAddressRequest();
        addr.setFirstName("John");
        addr.setLastName("Doe");
        addr.setPhone("05000000000");
        addr.setEmail("john@example.com");
        addr.setAddress("Test St 1");
        addr.setCity("Istanbul");
        addr.setCountry("Turkey");
        req.setShippingAddress(addr);

        CardRequest card = new CardRequest();
        card.setHolderName("John Doe");
        card.setNumber("5528790000000008");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");
        req.setCard(card);

        return req;
    }

    private Order buildOrder(Long id) {
        return Order.builder()
                .id(id)
                .userId(USER_ID)
                .username(USERNAME)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("199.80"))
                .currency("TRY")
                .build();
    }

    private OrderItem buildOrderItem() {
        return OrderItem.builder()
                .productId(1L)
                .productName("Test Product")
                .unitPrice(new BigDecimal("99.90"))
                .quantity(2)
                .build();
    }

    private OrderResponse buildResponse(Long id) {
        return OrderResponse.builder()
                .id(id)
                .userId(USER_ID)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("199.80"))
                .currency("TRY")
                .build();
    }
}
