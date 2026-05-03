# Saga Flow — Detaylı Sequence Diagram

## Happy Path (Başarılı Sipariş)

```
Frontend          API Gateway      Order Service      Outbox      RabbitMQ      Stock Service     Iyzico       Notification
   │                  │                  │              │            │                │             │                │
   │ POST /orders     │                  │              │            │                │             │                │
   │ Idempotency-Key  │                  │              │            │                │             │                │
   ├─────────────────►│                  │              │            │                │             │                │
   │                  │ JWT validate     │              │            │                │             │                │
   │                  │ X-User-Id ekle   │              │            │                │             │                │
   │                  ├─────────────────►│              │            │                │             │                │
   │                  │                  │ check idem.  │            │                │             │                │
   │                  │                  │ key (UNIQUE) │            │                │             │                │
   │                  │                  │              │            │                │             │                │
   │                  │                  │ Order(PENDING)│           │                │             │                │
   │                  │                  ├──────────────►            │                │             │                │
   │                  │                  │              │            │                │             │                │
   │                  │                  │ OutboxEvent  │            │                │             │                │
   │                  │                  │ (OrderCreated)│           │                │             │                │
   │                  │                  ├──────────────►            │                │             │                │
   │                  │                  │              │            │                │             │                │
   │                  │                  │ Card → Redis │            │                │             │                │
   │                  │                  │ (15dk TTL)   │            │                │             │                │
   │                  │ ◄──────────────┤              │            │                │             │                │
   │ ◄────────────────┤ 201 PENDING      │              │            │                │             │                │
   │                  │                  │              │            │                │             │                │
   │                                                    │            │                │             │                │
   │                            ⏰ Outbox Publisher (her 2sn) çalışır                 │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ poll unpublished events (SKIP LOCKED)      │             │                │
   │                                     │              │            │                │             │                │
   │                                     │              │ pending evt│                │             │                │
   │                                     │ ◄────────────┤            │                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ publish      │            │                │             │                │
   │                                     ├─────────────────────────►│                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │              │            │ OrderCreated   │             │                │
   │                                     │              │            ├──────────────►│             │                │
   │                                     │              │            │                │             │                │
   │                                     │              │            │     SELECT FOR UPDATE        │                │
   │                                     │              │            │     stoğu düş                │                │
   │                                     │              │            │                │             │                │
   │                                     │              │            │ StockReserved  │             │                │
   │                                     │              │            │ ◄──────────────┤             │                │
   │                                     │              │            │                │             │                │
   │                                     │              │            │ to order queue │             │                │
   │                                     │              │            │                │             │                │
   │                            @RabbitListener StockReserved        │                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ Order: STOCK_RESERVED     │                │             │                │
   │                                     │ → PAYMENT_PROCESSING      │                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ getCard(orderId) from Redis                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ POST /payment (Iyzico SDK)                 │             │                │
   │                                     ├───────────────────────────────────────────────────────►│                │
   │                                     │                                            │             │                │
   │                                     │ paymentId, success                         │             │                │
   │                                     │ ◄───────────────────────────────────────────────────────┤                │
   │                                     │              │            │                │             │                │
   │                                     │ Order: COMPLETED          │                │             │                │
   │                                     │              │            │                │             │                │
   │                                     │ OutboxEvent (OrderCompleted)               │             │                │
   │                                     ├──────────────►            │                │             │                │
   │                                     │              │            │                │             │                │
   │                            ⏰ Outbox Publisher                  │                │             │                │
   │                                     │              │            │ OrderCompleted │             │                │
   │                                     │              │            ├──────────────────────────────────────────────►│
   │                                     │              │            │                │             │                │
   │                                     │              │            │                │             │  send email    │
   │                                     │              │            │                │             │  WebSocket push│
   │                                     │              │            │                │             │  (admin DB)    │
   │                                                                                                                  │
```

## Compensation Path (Stok Yetersiz)

```
Order Service ──OrderCreated──► Stock Service
                                    │
                                    │ stok yetersiz
                                    ▼
                              StockRejected
                                    │
                                    ▼
Order Service ◄─────────────────────┘
    │
    ▼
Order: CANCELLED + OrderCancelledEvent → Notification (mail "üzgünüz, stok kalmadı")
```

## Compensation Path (Payment Failed)

```
Order Service ─StockReserved─► Iyzico (FAIL)
                                    │
                                    ▼
PaymentFailed ──────────────► Stock Service (release stok = compensation)
    │
    ▼
Order: CANCELLED + OrderCancelledEvent → Notification
```

## Failure Modes ve Çözümleri

| Failure | Etki | Çözüm |
|---------|------|-------|
| RabbitMQ down | Event publish edilemez | Outbox tablosunda bekler, publisher retry yapar |
| Stock Service down | Saga ilk adımda kalır | Order PENDING durumunda kalır, ops alarm + manuel intervention |
| Iyzico down/timeout | Payment hatası | PaymentFailed → stok release (compensation) |
| Order Service crash | Order yarım kalabilir | Outbox event'leri persist olduğu için restart sonrası publisher devam eder |
| Duplicate event | At-least-once delivery sonucu | OrderStatus state guard duplicate transition'ı engeller |
| Card cache TTL bitti (15dk) | Payment yapılamaz | Order CANCELLED, kullanıcı tekrar denemeli |

## Sequence Diagram Mermaid

Gerçek diagram için bu kodu mermaid.live'a yapıştır:

```mermaid
sequenceDiagram
    actor U as User
    participant FE as Frontend
    participant GW as API Gateway
    participant OS as Order Svc
    participant DB as PostgreSQL
    participant MQ as RabbitMQ
    participant SS as Stock Svc
    participant IZ as Iyzico
    participant NS as Notification Svc

    U->>FE: Checkout
    FE->>GW: POST /orders + Idem-Key
    GW->>OS: forward (X-User-Id)
    OS->>DB: BEGIN TX
    OS->>DB: INSERT Order (PENDING)
    OS->>DB: INSERT OutboxEvent
    OS->>DB: COMMIT
    OS-->>GW: 201 PENDING
    GW-->>FE: 201
    
    Note over OS: ⏰ Scheduled Publisher
    OS->>DB: SELECT FOR UPDATE SKIP LOCKED
    OS->>MQ: publish OrderCreated
    OS->>DB: mark published
    
    MQ->>SS: OrderCreated
    SS->>DB: SELECT FOR UPDATE
    SS->>DB: UPDATE stock
    SS->>MQ: StockReserved
    
    MQ->>OS: StockReserved
    OS->>DB: status=STOCK_RESERVED
    OS->>OS: getCard from Redis
    OS->>IZ: createPayment
    IZ-->>OS: success
    OS->>DB: status=COMPLETED + OutboxEvent
    
    Note over OS: Publisher
    OS->>MQ: OrderCompleted
    MQ->>NS: OrderCompleted
    NS->>NS: send email
    NS->>FE: WebSocket push (admin)
```
