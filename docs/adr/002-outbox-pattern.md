# ADR 002: Reliable Event Publishing için Outbox Pattern

**Tarih:** 2026-04
**Durum:** Kabul edildi

## Bağlam

Saga akışında, order-service'in OrderCreatedEvent yayınlaması gerekiyor.

Naive yaklaşım:
```java
@Transactional
public void createOrder(...) {
    orderRepo.save(order);                     // (1) DB write
    rabbitTemplate.send(orderCreatedEvent);    // (2) RabbitMQ publish
}
```

**Sorun:** (1) ve (2) atomic değil. Aşağıdaki senaryolar problem yaratır:

- (1) başarılı, (2) başarısız (RabbitMQ down) → order DB'de var ama event yok → stok hiç düşmez → **inconsistent state**
- (1) başarılı, (2) ağ hatası nedeniyle hem başarılı hem değil → duplicate publish riski

## Değerlendirilen Seçenekler

### Seçenek A: Try-catch + Retry
```java
try { rabbitTemplate.send(...); } catch { retry... }
```
- ❌ Process crash olursa retry kaybolur
- ❌ Application code'u karmaşıklaştırır

### Seçenek B: Two-Phase Commit (Postgres-RabbitMQ XA)
- ❌ RabbitMQ XA-aware değil (default config)
- ❌ Performance kötü

### Seçenek C: Outbox Pattern ⭐
- ✅ Event'i DB transaction'ı ile **aynı tablo**ya yaz
- ✅ Ayrı bir publisher (scheduled job veya CDC) outbox'tan oku, RabbitMQ'ya gönder
- ✅ At-least-once delivery garantili
- ✅ RabbitMQ down ise sadece publish gecikir, hiçbir şey kaybolmaz

### Seçenek D: Debezium + CDC
- ✅ Postgres WAL'dan otomatik event okuma
- ❌ Setup karmaşık (Kafka Connect, Debezium image)
- ❌ Bu projenin scope'u için over-engineering

## Karar

**Application-level Outbox Pattern** + **scheduled publisher** kullanılacak.

## Implementasyon

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64),
    aggregate_id VARCHAR(64),
    event_type VARCHAR(64),
    routing_key VARCHAR(128),
    payload TEXT,
    published BOOLEAN DEFAULT FALSE,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
```

```java
@Transactional
public void createOrder(...) {
    Order saved = orderRepo.save(order);
    outboxRepo.save(new OutboxEvent(...event...));  // AYNI TRANSACTION
}

// Ayrı bean:
@Scheduled(fixedDelay = 2000)
@Transactional
public void publishPending() {
    var pending = outboxRepo.findUnpublishedForUpdate(...);  // SKIP LOCKED
    for (var event : pending) {
        rabbitTemplate.send(...);
        event.markPublished();
    }
}
```

### Önemli Detaylar

- **`SELECT ... FOR UPDATE SKIP LOCKED`**: Multi-instance deployment'ta publisher'lar çakışmaz, her biri kendi alabildiğini işler.
- **Retry mekanizması**: `retry_count` alanı, `max_retries` (5) aşan event'ler manuel inceleme için DLQ'ya alınır.
- **Idempotency consumer-side**: At-least-once → consumer'lar duplicate event geleceğini varsayar (state guard).

## Sonuçları

- **+** Event delivery güvencesi
- **+** Order creation hızını etkilemez (publish async)
- **−** Eventual delivery (publish gecikebilir 2sn'ye kadar)
- **−** Outbox tablosu büyür → retention policy (7 gün) ileride eklenecek

## Referanslar

- Chris Richardson, [Pattern: Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
