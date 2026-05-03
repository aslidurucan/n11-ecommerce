package com.n11bootcamp.order.integration;

import com.n11bootcamp.order.dto.CardRequest;
import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.dto.ShippingAddressRequest;
import com.n11bootcamp.order.entity.Order;
import com.n11bootcamp.order.entity.OrderStatus;
import com.n11bootcamp.order.entity.OutboxEvent;
import com.n11bootcamp.order.repository.OrderRepository;
import com.n11bootcamp.order.repository.OutboxEventRepository;
import com.n11bootcamp.order.service.OrderService;
import com.n11bootcamp.order.service.payment.CardCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OrderService — Sipariş Oluşturma Entegrasyon Testleri")
class OrderCreationIntegrationTest {

    @TestConfiguration
    static class SecurityOverride {
        @Bean @Primary
        JwtDecoder mockJwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user-id")
                    .claim("preferred_username", "testuser")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orderdb")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    }

    @MockitoBean
    private CardCacheService cardCacheService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Sipariş oluşturulunca DB'ye persist edilir, status PENDING olur")
    void createOrder_persistedWithPendingStatus() {
        CreateOrderRequest request = buildOrderRequest(
                "user-1", new BigDecimal("150.00"), 3);

        OrderResponse response = orderService.createOrder(
                "user-1", "testuser", "idem-key-1", request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("450.00");

        Optional<Order> saved = orderRepository.findById(response.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getUserId()).isEqualTo("user-1");
        assertThat(saved.get().getIdempotencyKey()).isEqualTo("idem-key-1");
    }

    @Test
    @DisplayName("Sipariş oluşturulunca aynı transaction'da Outbox event yazılır")
    void createOrder_outboxEventWrittenInSameTransaction() {
        CreateOrderRequest request = buildOrderRequest(
                "user-2", new BigDecimal("500.00"), 2);

        OrderResponse response = orderService.createOrder(
                "user-2", "testuser2", "idem-key-2", request);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getAggregateId()).isEqualTo(response.getId().toString());
        assertThat(event.getEventType()).isEqualTo("OrderCreatedEvent");
        assertThat(event.getRoutingKey()).isEqualTo("order.stock.reserve.requested");
        assertThat(event.getPublished()).isFalse();
        assertThat(event.getPayload()).contains("\"orderId\"");
    }

    @Test
    @DisplayName("Birden fazla sipariş: her biri için ayrı Outbox event yazılır")
    void createOrder_multipleOrders_separateOutboxEventsCreated() {
        orderService.createOrder("u1", "user1", "key-a",
                buildOrderRequest("u1", new BigDecimal("100.00"), 1));
        orderService.createOrder("u2", "user2", "key-b",
                buildOrderRequest("u2", new BigDecimal("200.00"), 2));

        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Aynı idempotency key ikinci kez gelince mevcut sipariş döner, yeni kayıt oluşmaz")
    void createOrder_sameIdempotencyKey_returnsExistingOrderWithoutDuplicate() {
        CreateOrderRequest request = buildOrderRequest(
                "user-3", new BigDecimal("300.00"), 1);

        OrderResponse first = orderService.createOrder(
                "user-3", "testuser3", "idem-key-dup", request);
        OrderResponse second = orderService.createOrder(
                "user-3", "testuser3", "idem-key-dup", request);

        assertThat(second.getId()).isEqualTo(first.getId());

        assertThat(orderRepository.count()).isEqualTo(1);

        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Null idempotency key ile sipariş oluşturulunca IllegalArgumentException fırlar")
    void createOrder_nullIdempotencyKey_throwsIllegalArgumentException() {
        CreateOrderRequest request = buildOrderRequest(
                "user-4", new BigDecimal("100.00"), 1);

        assertThatThrownBy(() ->
                orderService.createOrder("user-4", "testuser4", null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sıfır tutarlı sipariş oluşturulunca IllegalArgumentException fırlar")
    void createOrder_zeroTotal_throwsIllegalArgumentException() {
        CreateOrderRequest request = buildOrderRequest(
                "user-5", BigDecimal.ZERO, 1);

        assertThatThrownBy(() ->
                orderService.createOrder("user-5", "testuser5", "idem-key-zero", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("Sipariş durumu PENDING → STOCK_RESERVED geçişi DB'ye yansır")
    void updateOrderStatus_transitionPersistedInDatabase() {
        OrderResponse created = orderService.createOrder(
                "user-6", "testuser6", "idem-key-status",
                buildOrderRequest("user-6", new BigDecimal("200.00"), 1));

        orderService.updateOrderStatus(created.getId(), OrderStatus.STOCK_RESERVED);

        Order fromDb = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
    }

    @Test
    @DisplayName("Sipariş iptal edilince status CANCELLED, sebep DB'ye yazılır")
    void cancelOrder_statusAndReasonPersistedInDatabase() {
        OrderResponse created = orderService.createOrder(
                "user-7", "testuser7", "idem-key-cancel",
                buildOrderRequest("user-7", new BigDecimal("100.00"), 1));

        orderService.cancelOrder(created.getId(), "Stok yetersiz");

        Order fromDb = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(fromDb.getPaymentFailureReason()).isEqualTo("Stok yetersiz");
    }

    private CreateOrderRequest buildOrderRequest(String userId,
                                                  BigDecimal unitPrice, int quantity) {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId(1001L);
        item.setProductName("Test Ürünü");
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);

        ShippingAddressRequest addr = new ShippingAddressRequest();
        addr.setFirstName("Aslı");
        addr.setLastName("Durucan");
        addr.setEmail("asli@test.com");
        addr.setPhone("05001234567");
        addr.setAddress("Test Caddesi No:1");
        addr.setCity("İstanbul");
        addr.setCountry("TR");

        CardRequest card = new CardRequest();
        card.setHolderName("ASLI DURUCAN");
        card.setNumber("5528790000000008");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        request.setShippingAddress(addr);
        request.setCard(card);
        return request;
    }
}
