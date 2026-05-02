# 🛒 n11 E-Commerce Platform

> N11 TalentHub Backend Bootcamp — Bitirme Projesi
> **Production-grade microservices e-commerce platform**

Spring Boot 3 + Spring Cloud 2024 ile geliştirilmiş, Saga Pattern ve Outbox Pattern üzerine kurulu mikroservis e-ticaret platformu.

---

## 🎯 Bu Projeyi Diğerlerinden Ayıran Özellikler

| Özellik | Neden Önemli |
|---------|--------------|
| 🔄 **Outbox Pattern** | Domain event'lerin DB transaction'ı ile aynı anda yazılması — RabbitMQ down olsa bile event kaybolmaz. **At-least-once delivery garantisi.** |
| 🎭 **Saga Pattern (Choreography)** | Distributed transaction yerine eventually-consistent akış. Order → Stock → Payment zinciri compensation event'leri ile telafi edilir. |
| 🔑 **Idempotency Key** | Aynı checkout isteği iki kez gelirse tek order oluşur. Stripe/Iyzico'nun da kullandığı endüstri standardı pattern. |
| 🔒 **Pessimistic Locking** | `SELECT ... FOR UPDATE` ile concurrent stok çekimlerinde race condition'a kapalı. |
| 🏛️ **Keycloak OAuth2** | Manuel JWT yerine endüstri-standardı IAM. SSO, role management, refresh token, password reset — hepsi hazır. |
| 💳 **Iyzico Real Integration** | Sandbox değil mock değil — gerçek SDK ile gerçek payment akışı. |
| ⚡ **Circuit Breaker (Resilience4j)** | Feign Client çağrılarında downstream service down olduğunda graceful degradation. |
| 📊 **Spring Boot Actuator + Prometheus** | Health, metrics, tracing — production-ready observability. |
| 🐳 **Jib Build (Dockerfile-less)** | Daha hızlı, daha küçük, daha güvenli container imajları. |

---

## 🏗️ Mimari

```
                                ┌──────────────────┐
                                │  React Frontend  │
                                │  (Vite + TS)     │
                                └────────┬─────────┘
                                         │
                                ┌────────▼─────────┐
                                │  API Gateway     │  ← JWT validation
                                │  (Spring Cloud)  │  ← Rate limit
                                │     :8080        │  ← CORS
                                └────────┬─────────┘
                                         │
            ┌────────────┬───────────────┼──────────────┬──────────────┐
            │            │               │              │              │
       ┌────▼─────┐ ┌────▼─────┐  ┌─────▼─────┐ ┌──────▼──────┐ ┌────▼──────┐
       │   User   │ │ Product  │  │   Cart    │ │   Order     │ │  Stock    │
       │  Service │ │ Service  │  │  Service  │ │  Service    │ │  Service  │
       │  :8081   │ │  :8082   │  │   :8083   │ │   :8084     │ │   :8085   │
       └────┬─────┘ └────┬─────┘  └─────┬─────┘ └──────┬──────┘ └────┬──────┘
            │            │              │              │             │
            ▼            ▼              ▼              ▼             ▼
       ┌────────┐  ┌─────────┐    ┌─────────┐  ┌────────────┐  ┌────────┐
       │Postgres│  │Postgres │    │  Redis  │  │ Postgres   │  │Postgres│
       │ userdb │  │productdb│    │  cart   │  │ + Outbox   │  │stockdb │
       └────────┘  └─────────┘    └─────────┘  └────────────┘  └────────┘
                                                     │
                                                     ▼
                                        ┌──────────────────────┐
                                        │  Notification Svc    │
                                        │  (Mail + WebSocket)  │
                                        │       :8086          │
                                        └──────────────────────┘

   ╔═══════════════════════════════════════════════════════════╗
   ║                   Cross-cutting Services                  ║
   ╠═══════════════════════════════════════════════════════════╣
   ║  Eureka Server  :8761  → Service Discovery               ║
   ║  Config Server  :8762  → Centralized Configuration       ║
   ║  RabbitMQ       :5672  → Saga Event Bus + DLX/DLQ        ║
   ║  Keycloak       :8090  → OAuth2 / OIDC Identity Provider ║
   ╚═══════════════════════════════════════════════════════════╝
```

### Servisler Özeti

| Servis | Port | Sorumluluk | Önemli Pattern'ler |
|--------|------|------------|---------------------|
| **eureka-server** | 8761 | Service registry | Service Discovery |
| **config-server** | 8762 | Merkezi config | Spring Cloud Config Native |
| **api-gateway** | 8080 | Reverse proxy + JWT | OAuth2 Resource Server, Rate Limit |
| **user-service** | 8081 | Profile, address | Keycloak Admin Client |
| **product-service** | 8082 | Catalog, search | i18n (Product+Translation), Redis Cache |
| **cart-service** | 8083 | Sepet | Redis-backed (no DB) |
| **order-service** | 8084 | Sipariş + Saga | **Outbox**, **Saga**, **Idempotency**, Iyzico |
| **stock-service** | 8085 | Stok + rezervasyon | **Pessimistic Lock**, Saga Listener |
| **notification-service** | 8086 | Mail + WebSocket | STOMP, JavaMailSender |

---

## 🔄 Saga Akışı (Choreography)

Bir kullanıcı checkout yaptığında olan akış:

```
1. POST /api/orders (Idempotency-Key header ile)
        │
        ▼
2. ┌─────────────────────────────────────────┐
   │ Order DB'ye PENDING olarak yazılır      │
   │ + OrderCreatedEvent outbox tablosuna    │  ← AYNI TRANSACTION
   │ + Card → Redis (15dk TTL, PCI-DSS)      │
   └─────────────────────────────────────────┘
        │
        ▼ (transaction commit)
3. OutboxPublisher (her 2sn) → RabbitMQ'ya publish eder
        │
        ▼
4. Stock Service event'i alır:
   ├─ OK  → SELECT ... FOR UPDATE → stoğu düş → StockReservedEvent
   └─ FAIL → StockRejectedEvent (yetersiz)
        │
        ▼
5. Order Service:
   ├─ StockReserved → Iyzico'ya charge → COMPLETED veya PAYMENT_FAILED
   └─ StockRejected → CANCELLED (terminal)
        │
        ▼
6. PAYMENT_FAILED → PaymentFailedEvent → Stock release (compensation)
   COMPLETED      → OrderCompletedEvent → Notification (mail + WebSocket push)
```

**Anahtar tasarım kararları:**
- Choreography (orchestrator yok) → daha az coupling, ama akışı izlemek zor olabilir
- At-least-once delivery + state guard → duplicate event'ler güvenli
- Compensation transactions (stoğu geri açma) iki-fazlı commit ihtiyacını ortadan kaldırır

Daha fazla detay: [`docs/saga-flow.md`](docs/saga-flow.md)

---

## 🚀 Kurulum

### Gereksinimler

- **Java 21**
- **Maven 3.9+**
- **Docker + Docker Compose**
- **Node.js 20+** (frontend için)
- **Iyzico sandbox API key** ([almak için](https://sandbox-merchant.iyzipay.com/))

### Adım Adım

**1. Repo'yu klonla:**
```bash
git clone <repo>
cd n11-ecommerce
```

**2. Environment dosyasını oluştur (kök dizinde `.env`):**
```bash
IYZICO_API_KEY=<sandbox-api-key>
IYZICO_SECRET_KEY=<sandbox-secret-key>
SMTP_USERNAME=<gmail-address>
SMTP_PASSWORD=<gmail-app-password>
SLACK_WEBHOOK_URL=<optional>
```

**3. Altyapı servislerini başlat:**
```bash
docker-compose up -d
# ~30sn içinde Postgres, Redis, RabbitMQ, Keycloak hazır.
```

**4. Keycloak realm'ı yükle:**
```bash
# İlk başta:
# - http://localhost:8090 → admin/admin
# - Realm "ecommerce" oluştur
# - Client "ecommerce-app" (public, password grant açık)
# - Roles: USER, ADMIN
# - Test user: testuser/test123
# Sonra exporta: docs/keycloak-setup.md
```

**5. Backend'i çalıştır (geliştirme için IDE'den, sırasıyla):**
```bash
cd backend
mvn -pl eureka-server spring-boot:run    # Önce bu, 30sn bekle
mvn -pl config-server spring-boot:run    # Sonra bu, 20sn bekle
mvn -pl api-gateway spring-boot:run      # Paralel başlat:
mvn -pl user-service spring-boot:run
mvn -pl product-service spring-boot:run
mvn -pl cart-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl stock-service spring-boot:run
mvn -pl notification-service spring-boot:run
```

**6. Frontend'i başlat:**
```bash
cd frontend
npm install
npm run dev
# http://localhost:3000
```

**7. Test:**
- API Gateway: http://localhost:8080
- Eureka: http://localhost:8761 (tüm servisleri görmeli)
- RabbitMQ Management: http://localhost:15672 (guest/guest)
- Swagger: http://localhost:8080/swagger-ui.html (her servis için ayrı)
- Keycloak: http://localhost:8090

---

## 🐳 Tam Stack Docker Deployment

Geliştirme için yukarıdaki IDE-tabanlı yöntem hızlıdır. Ama **tek komutla tüm sistemi container'larda çalıştırmak** istersen:

### Mimari

```
docker-compose.yml          → Altyapı (Postgres, Redis, RabbitMQ, Keycloak)
docker-compose.full.yml     → Backend (9 mikroservis) + Frontend (Nginx)
```

### Adım 1 — Backend image'larını build et (Jib ile)

Spring Boot servisleri için **Dockerfile yok** — Jib her servisi otomatik container'a paketler:

```bash
cd backend
mvn -B compile jib:dockerBuild -DskipTests \
  -pl eureka-server,config-server,api-gateway,user-service,product-service,cart-service,order-service,stock-service,notification-service
```

Build sonunda 9 image lokal Docker daemon'da hazır:
```
n11ecommerce/eureka-server:1.0.0
n11ecommerce/config-server:1.0.0
n11ecommerce/api-gateway:1.0.0
n11ecommerce/user-service:1.0.0
n11ecommerce/product-service:1.0.0
n11ecommerce/cart-service:1.0.0
n11ecommerce/order-service:1.0.0
n11ecommerce/stock-service:1.0.0
n11ecommerce/notification-service:1.0.0
```

### Adım 2 — Tüm sistemi başlat

Frontend image'ı compose tarafından otomatik build edilir (multi-stage Node + Nginx):

```bash
cd ..
docker-compose -f docker-compose.yml -f docker-compose.full.yml up -d --build
```

İlk başlatmada ~2-3 dakika sürer (frontend image build + tüm servislerin healthcheck'leri).

### Adım 3 — Sistem durumunu kontrol et

```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml ps
```

Tüm servisler `healthy` veya `running` olmalı.

### Adım 4 — Erişim

| URL | Açıklama |
|---|---|
| http://localhost:3000 | Frontend (React + Nginx) |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:15672 | RabbitMQ Management (guest/guest) |
| http://localhost:8090 | Keycloak Admin |

### Adım 5 — Sistemi durdur

```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml down
```

Verileri silmek için:
```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml down -v
```

### CI/CD — GitHub Actions

Her push'ta:
1. **Test:** Postgres + RabbitMQ + Redis container'ları ayağa kalkar, tüm unit testler çalışır
2. **Build:** Jib ile 9 backend servisi paralel olarak image'a dönüşür
3. **Notify:** Sonuç Slack'e bildirilir (opsiyonel)

`.github/workflows/ci.yml` dosyasında detaylar.

---

## 🧪 Testler

Toplam **130 unit test** — JUnit 5 + Mockito + AssertJ.

```bash
# Tüm testler
cd backend
mvn test

# Belirli servis
mvn -pl order-service test

# Spesifik test class
mvn -pl order-service test -Dtest=OrderServiceIdempotencyTest
```

### Test Kapsamı

| Servis | Test Dosyası | Test Sayısı |
|---|---|---|
| user-service | UserServiceImplTest, UserControllerTest | 17 |
| product-service | ProductServiceImplTest, ProductMapperTest, ProductControllerTest | 23 |
| cart-service | CartTest, CartServiceImplTest, CartControllerTest | 25 |
| order-service | OrderServiceImplTest, OrderControllerTest, OrderServiceIdempotencyTest, OutboxPublisherTest, OrderTest, OrderStatusTest | 41 |
| stock-service | StockServiceImplTest, StockControllerTest | 20 |
| notification-service | NotificationEventListenerTest | 4 |

**Önemli testler:**
- `OrderServiceIdempotencyTest` — aynı `Idempotency-Key` 2 kez gönderilse de tek order yaratılır
- `OutboxPublisherTest` — Outbox pattern: RabbitMQ down olsa bile event'ler eninde sonunda yayınlanır
- `StockServiceImplTest` — pessimistic lock + reservation lifecycle (reserve → commit / release)
- `OrderStatusTest` — sipariş durum makinesi (PENDING → PAID → COMPLETED veya iptal akışları)

---

## 📡 API Örnekleri

### 1. Login (Keycloak'tan token al)
```bash
curl -X POST http://localhost:8090/realms/ecommerce/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=ecommerce-app&username=testuser&password=test123"
```

### 2. Sipariş Oluştur (Idempotency-Key ile)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [{"productId": 1, "productName": "T-Shirt", "unitPrice": 99.90, "quantity": 2}],
    "firstName": "John", "lastName": "Doe", "email": "j@example.com",
    "phone": "+90...", "address": "...", "city": "Istanbul", "country": "Turkey",
    "card": {"holderName": "John Doe", "number": "5528790000000008",
             "expireMonth": "12", "expireYear": "2030", "cvc": "123"}
  }'
```

**Test kartları:** [Iyzico test cards](https://dev.iyzipay.com/en/test-cards)
- Başarılı: `5528790000000008`
- Başarısız: `4111111111111129`

---

## 📊 Mülakat İçin Hazırlık

### "Neden Choreography Saga?"
Servis sayısı az (5), orchestrator overhead'i istemedim. Coupling de düşük olur. Trade-off: akışı izlemek için distributed tracing (Sleuth/Zipkin) eklemek gerekir.

### "Outbox neden gerekli?"
Order DB'ye yazılıp ardından RabbitMQ publish başarısız olursa: order var, ama event yok → inconsistent state. Outbox bu sorunu **DB transaction garantisi** ile çözer. RabbitMQ sonra ne olursa olsun event sonunda publish edilir.

### "İki kişi son ürünü aynı anda alırsa?"
Stock-service `@Lock(PESSIMISTIC_WRITE)` ile çekiyor (`SELECT ... FOR UPDATE`). İlk transaction lock alır, ikinci bekler. Lock release olunca stoğun yetersiz olduğunu görür → StockRejectedEvent.

### "Idempotency neden önemli?"
Kullanıcı submit'e iki kez basabilir, network glitch retry yapabilir, mobile app double-tap olabilir. Idempotency-Key UNIQUE constraint sayesinde DB seviyesinde duplicate engellenir. Stripe da aynısını yapıyor.

### "Multi-instance deployment'ta Outbox publisher çakışmaz mı?"
`SELECT ... FOR UPDATE SKIP LOCKED` kullanıyorum (Postgres'in özelliği). Bir publisher kilitlediği satırı diğeri SKIP eder, duplicate publish olmaz.

### "PCI-DSS uyumu nasıl?"
Kart numarası DB'ye **hiç** yazılmıyor. Iyzico'ya direkt iletiliyor, sadece paymentId (token) saklanıyor. Saga akışı sırasında 15dk Redis TTL ile geçici cache var, sonra otomatik silinir.

---

## 📚 Daha Fazla Doküman

- [`docs/adr/`](docs/adr/) — Architecture Decision Records (neden bu kararları aldık)
- [`docs/saga-flow.md`](docs/saga-flow.md) — Detaylı sequence diagram
- [`docs/api-reference.md`](docs/api-reference.md) — Tüm endpoint'ler
- [`docs/keycloak-setup.md`](docs/keycloak-setup.md) — Realm setup adımları

---

## 👤 Geliştirici

Hazırlayan: **[Adın]**
Bootcamp: **N11 TalentHub Backend Bootcamp** (patika.dev × n11)
Teslim: 2026

---

## 📄 Lisans

MIT