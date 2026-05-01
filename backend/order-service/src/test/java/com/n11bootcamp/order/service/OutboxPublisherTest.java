package com.n11bootcamp.order.service;

import com.n11bootcamp.order.entity.OutboxEvent;
import com.n11bootcamp.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxPublisher davranış testleri.
 *
 * Outbox pattern'in en kritik garantisi: "RabbitMQ down olsa bile event kaybolmaz."
 * Bu test, davranışın kod seviyesinde gerçekten çalıştığını kanıtlar.
 *
 * Test edilen 4 senaryo:
 *  1) Happy path — N event publish edilir, hepsi 'published=true' olur
 *  2) RabbitMQ down — AmqpException fırlar; event'ler markFailed olur,
 *     'published=false' kalır, retryCount artar — KAYIP YOK
 *  3) Boş outbox — RabbitTemplate hiç çağrılmaz (no-op davranış)
 *  4) Karışık — bazı event başarılı, bazısı fail; her biri kendi durumunu alır
 *
 * NOT: LENIENT strictness kullanılıyor çünkü OutboxPublisher batch processor —
 * aynı RabbitTemplate.send metodunu N farklı argümanla çağırır. Strict mode
 * bunu yanlış pozitif "PotentialStubbingProblem" olarak işaretler.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxPublisher — RabbitMQ failure resilience")
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private static final String EXCHANGE = "saga-events";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "exchange", EXCHANGE);
    }

    // ====================================================================
    // SENARYO 1: HAPPY PATH — Tüm event'ler başarıyla publish edilir
    // ====================================================================
    @Test
    @DisplayName("Happy path: bekleyen event'ler RabbitMQ'ya gönderilir ve markPublished olur")
    void publishPending_whenAllSucceed_marksAllAsPublished() {
        // GIVEN: 3 bekleyen event
        OutboxEvent e1 = buildPendingEvent(1L, "stock.reserve.requested");
        OutboxEvent e2 = buildPendingEvent(2L, "order.completed");
        OutboxEvent e3 = buildPendingEvent(3L, "payment.failed");

        when(outboxEventRepository.findUnpublishedForUpdate(any(Integer.class), any(Pageable.class)))
                .thenReturn(List.of(e1, e2, e3));

        // WHEN
        outboxPublisher.publishPending();

        // THEN: 3 event de RabbitMQ'ya gitmiş
        verify(rabbitTemplate, times(3)).send(eq(EXCHANGE), anyString(), any(Message.class));

        // Hepsi 'published=true' olmuş
        assertThat(e1.getPublished()).isTrue();
        assertThat(e2.getPublished()).isTrue();
        assertThat(e3.getPublished()).isTrue();

        // Hiçbirinde retry artmamış
        assertThat(e1.getRetryCount()).isEqualTo(0);
        assertThat(e2.getRetryCount()).isEqualTo(0);
        assertThat(e3.getRetryCount()).isEqualTo(0);
    }

    // ====================================================================
    // SENARYO 2: RabbitMQ DOWN — Outbox'ın en önemli garantisi
    // ====================================================================
    @Test
    @DisplayName("RabbitMQ down: event'ler kaybolmaz, markFailed olur, retryCount artar")
    void publishPending_whenRabbitMqThrows_marksAsFailedAndKeepsEventForRetry() {
        // GIVEN: 2 bekleyen event
        OutboxEvent e1 = buildPendingEvent(1L, "stock.reserve.requested");
        OutboxEvent e2 = buildPendingEvent(2L, "order.completed");

        when(outboxEventRepository.findUnpublishedForUpdate(any(Integer.class), any(Pageable.class)))
                .thenReturn(List.of(e1, e2));

        // RabbitMQ tüm send çağrılarında patlasın
        doThrow(new AmqpException("Connection refused: RabbitMQ down"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        // WHEN
        outboxPublisher.publishPending();

        // THEN: KRİTİK — event'ler hâlâ 'published=false', sonraki run'da tekrar denenecek
        assertThat(e1.getPublished()).isFalse();
        assertThat(e2.getPublished()).isFalse();

        // RetryCount artmış, son hata mesajı kaydedilmiş
        assertThat(e1.getRetryCount()).isEqualTo(1);
        assertThat(e2.getRetryCount()).isEqualTo(1);
        assertThat(e1.getLastError()).contains("Connection refused");
        assertThat(e2.getLastError()).contains("Connection refused");

        // RabbitTemplate yine de 2 kez denenmiş (her event için bir kez)
        verify(rabbitTemplate, times(2)).send(eq(EXCHANGE), anyString(), any(Message.class));
    }

    // ====================================================================
    // SENARYO 3: BOŞ OUTBOX — Hiç event yoksa RabbitTemplate çağrılmamalı
    // ====================================================================
    @Test
    @DisplayName("Boş outbox: RabbitTemplate hiç çağrılmaz")
    void publishPending_whenNoPendingEvents_doesNothing() {
        // GIVEN: Boş liste
        when(outboxEventRepository.findUnpublishedForUpdate(any(Integer.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // WHEN
        outboxPublisher.publishPending();

        // THEN: RabbitMQ'ya hiç istek gitmemiş
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    // ====================================================================
    // SENARYO 4: KARIŞIK — bazı başarılı, bazısı fail
    // ====================================================================
    @Test
    @DisplayName("Karışık senaryo: bazı event'ler başarılı, bazıları fail; her biri kendi durumunu alır")
    void publishPending_whenPartialFailure_independentEventStates() {
        // GIVEN: 3 event
        OutboxEvent e1 = buildPendingEvent(1L, "stock.reserve.requested");
        OutboxEvent e2 = buildPendingEvent(2L, "order.completed");
        OutboxEvent e3 = buildPendingEvent(3L, "payment.failed");

        when(outboxEventRepository.findUnpublishedForUpdate(any(Integer.class), any(Pageable.class)))
                .thenReturn(List.of(e1, e2, e3));

        // Sadece "order.completed" routing key'inde patlasın
        // LENIENT mode sayesinde diğer çağrılar default no-op davranışına düşer (= success)
        doThrow(new AmqpException("Routing failed for order.completed"))
                .when(rabbitTemplate).send(eq(EXCHANGE), eq("order.completed"), any(Message.class));

        // WHEN
        outboxPublisher.publishPending();

        // THEN: 1 ve 3 yayınlandı, 2 fail oldu
        assertThat(e1.getPublished()).isTrue();
        assertThat(e2.getPublished()).isFalse();
        assertThat(e3.getPublished()).isTrue();

        assertThat(e2.getRetryCount()).isEqualTo(1);
        assertThat(e2.getLastError()).contains("Routing failed");
    }

    // ====================================================================
    // TEST DATA BUILDER
    // ====================================================================
    private OutboxEvent buildPendingEvent(Long id, String routingKey) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("Order")
                .aggregateId(String.valueOf(id))
                .eventType("OrderCreatedEvent")
                .routingKey(routingKey)
                .payload("{\"orderId\":" + id + "}")
                .published(false)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
}