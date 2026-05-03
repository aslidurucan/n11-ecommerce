# 🛒 n11 E-Commerce Platform

> N11 TalentHub Backend Bootcamp — Bitirme Projesi

Spring Boot 3 + Spring Cloud 2024 ile geliştirilmiş, **Saga Pattern** ve **Outbox Pattern** üzerine kurulu mikroservis tabanlı e-ticaret platformu. Frontend React + TypeScript, AI destekli ürün arama ile.

---

## 🎯 Öne Çıkan Özellikler

| Özellik | Açıklama |
|---------|----------|
| 🔄 **Outbox Pattern** | Domain event'lerin DB transaction'ı ile aynı anda yazılması — RabbitMQ down olsa bile event kaybolmaz. At-least-once delivery garantisi. |
| 🎭 **Saga Pattern (Choreography)** | Distributed transaction yerine eventually-consistent akış. Order → Stock → Payment zinciri compensation event'leri ile telafi edilir. |
| 🔑 **Idempotency Key** | Aynı checkout isteği iki kez gelirse tek order oluşur. Stripe/Iyzico'nun da kullandığı endüstri standardı. |
| 🔒 **Pessimistic Locking** | `SELECT ... FOR UPDATE` ile concurrent stok çekimlerinde race condition'a kapalı. |
| 🤖 **AI Destekli Arama** | Chat-service Groq LLM ile doğal dil sorgularını yapısal filtrelere çevirir. LLM erişilemediğinde keyword fallback devreye girer. |
| 🏛️ **Keycloak OAuth2 / OIDC** | Manuel JWT yerine endüstri-standardı IAM. SSO, role management, refresh token, password reset hazır. |
| 💳 **Iyzico Real Integration** | Mock değil, gerçek SDK ile gerçek payment akışı (sandbox). |
| ⚡ **Circuit Breaker** | Resilience4j ile downstream service down olduğunda graceful degradation. |
| 🌐 **i18n (Multi-language)** | Ürün açıklamaları için `Accept-Language` ile TR/EN dil desteği. |
| 📡 **WebSocket Notifications** | STOMP ile sipariş durum güncellemeleri canlı olarak frontend'e push edilir. |
| 🐳 **Jib Build (Dockerfile-less)** | Backend servisleri için Dockerfile yok — Jib otomatik image üretir. |
| ⚙️ **CI/CD** | GitHub Actions + Jib matrix build + Slack notification. |

---

## 🏗️ Mimari

```
                          ┌──────────────────┐
                          │  React Frontend  │
                          │  (Vite + TS)     │
                          └────────┬─────────┘
                                   │
                          ┌────────▼─────────┐
                          │  API Gateway     │  ← JWT validation, CORS, Rate limit
                          │     :8080        │
                          └────────┬─────────┘
                                   │
   ┌───────────┬───────────┬───────┼────────┬───────────┬──────────────┐
   │           │           │       │        │           │              │
┌──▼───┐   ┌──▼────┐   ┌──▼──┐  ┌─▼───┐  ┌─▼────┐  ┌──▼──────┐  ┌────▼────┐
│ User │   │Product│   │Cart │  │Order│  │Stock │  │  Chat   │  │Notifi-  │
│:8081 │   │ :8082 │   │:8083│  │:8084│  │:8085 │  │  :8087  │  │cation   │
└──┬───┘   └───┬───┘   └──┬──┘  └─┬───┘  └──┬───┘  └────┬────┘  │ :8086   │
   │           │          │       │         │           │       └────┬────┘
   ▼           ▼          ▼       ▼         ▼           │            ▼
┌────────┐┌─────────┐  ┌─────┐ ┌──────┐ ┌────────┐    │      ┌──────────┐
│Postgres││Postgres │  │Redis│ │Postgr│ │Postgres│    │      │   SMTP   │
│ userdb ││productdb│  │cart │ │+Outbox│ │stockdb │    │      │WebSocket │
└────────┘└─────────┘  └─────┘ └──────┘ └────────┘    │      └──────────┘
                                  │                    │
                                  └────────────────────┘
                                          (Feign)

╔═══════════════════════════════════════════════════════════╗
║                   Cross-cutting Services                  ║
╠═══════════════════════════════════════════════════════════╣
║  Eureka Server  :8761  → Service Discovery               ║
║  Config Server  :8762  → Centralized Configuration       ║
║  RabbitMQ       :5672  → Saga Event Bus + DLX/DLQ        ║
║  Keycloak       :8090  → OAuth2 / OIDC Identity Provider ║
╚═══════════════════════════════════════════════════════════╝
```

### Servisler

| Servis | Port | Sorumluluk | Önemli Pattern'ler |
|--------|------|------------|---------------------|
| **eureka-server** | 8761 | Service registry | Service Discovery |
| **config-server** | 8762 | Merkezi config | Spring Cloud Config |
| **api-gateway** | 8080 | Reverse proxy + JWT | OAuth2 Resource Server |
| **user-service** | 8081 | Profile, address | Keycloak Admin Client |
| **product-service** | 8082 | Catalog, search | i18n, Redis Cache |
| **cart-service** | 8083 | Sepet | Redis-backed |
| **order-service** | 8084 | Sipariş, ödeme | **Outbox**, **Saga**, **Idempotency**, Iyzico |
| **stock-service** | 8085 | Stok rezervasyonu | **Pessimistic Lock**, Saga Listener |
| **notification-service** | 8086 | Mail + WebSocket | STOMP, SMTP |
| **chat-service** | 8087 | AI ürün arama | Groq LLM + keyword fallback, Caffeine cache |

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
- **Choreography (orchestrator yok)** → daha az coupling, ama akışı izlemek için distributed tracing gerekir
- **At-least-once delivery + state guard** → duplicate event'ler güvenli
- **Compensation transactions** (stoğu geri açma) iki-fazlı commit ihtiyacını ortadan kaldırır

Detaylı sequence diagram: [`docs/saga-flow.md`](docs/saga-flow.md)

---

## 🤖 AI Destekli Ürün Arama

`chat-service` doğal dil sorgularını yapısal ürün filtrelerine çevirir.

**İki katmanlı strateji:**
1. **Birincil:** Groq LLM (`llama-3.1-8b-instant`) — yüksek anlama kalitesi
2. **Fallback:** Keyword + regex extraction — Groq erişilemediğinde devreye girer

**Dinamik metadata:** Geçerli kategori ve marka listesi product-service'ten çekilir, Caffeine ile 10dk cache'lenir. Yeni kategori/marka eklendiğinde sistem otomatik tanır.

**Örnek sorgular:**
- `"1000 tl altı kulaklık"` → category=Ses, maxPrice=1000
- `"Apple ürünleri"` → brand=Apple
- `"5000-10000 arası laptop"` → category=Bilgisayar, minPrice=5000, maxPrice=10000

Frontend'de sağ alt köşede floating chat widget olarak entegredir.

---

## 🚀 Kurulum

### Gereksinimler

- **Java 21**
- **Maven 3.9+**
- **Docker + Docker Compose**
- **Node.js 20+** (frontend için)
- **Iyzico sandbox API key** ([almak için](https://sandbox-merchant.iyzipay.com/))
- **Groq API key** (opsiyonel, [console.groq.com/keys](https://console.groq.com/keys) — ücretsiz)

### Adım 1 — Repo'yu klonla

```bash
git clone https://github.com/aslidurucan/n11-ecommerce.git
cd n11-ecommerce
```

### Adım 2 — Environment dosyasını oluştur

`.env.example` dosyasını `.env` olarak kopyala ve değerleri doldur:

```bash
cp .env.example .env
```

Düzenlemen gereken değerler:
```dotenv
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<güçlü-bir-şifre>
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=<güçlü-bir-şifre>
IYZICO_API_KEY=<sandbox-api-key>
IYZICO_SECRET_KEY=<sandbox-secret-key>
GROQ_API_KEY=<opsiyonel-llm-key>
MAIL_PASSWORD=<gmail-app-password>
```

### Adım 3 — Altyapı servislerini başlat

```bash
docker-compose up -d
```

Bu komut PostgreSQL, Redis, RabbitMQ ve Keycloak'ı ayağa kaldırır (~30sn).

### Adım 4 — Keycloak realm'ı yükle

İlk kurulumda:
1. http://localhost:8090 → admin/admin
2. Realm: **ecommerce**
3. Client: **ecommerce-app** (public, password grant)
4. Roles: `USER`, `ADMIN`, `CLIENT_ADMIN`
5. Test kullanıcıları yarat

### Adım 5 — Backend'i çalıştır (geliştirme için)

```bash
cd backend
mvn -pl eureka-server spring-boot:run    # Önce bu, ~30sn bekle
mvn -pl config-server spring-boot:run    # Sonra bu, ~20sn bekle
# Sonra paralel başlat:
mvn -pl api-gateway spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl product-service spring-boot:run
mvn -pl cart-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl stock-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl chat-service spring-boot:run
```

### Adım 6 — Frontend'i başlat

```bash
cd frontend
npm install
npm run dev
```

http://localhost:3000 adresinden açılır.

---

## 🐳 Tam Stack Docker Deployment

Tek komutla tüm sistemi container'larda çalıştırmak için:

### Adım 1 — Backend image'larını build et (Jib ile)

Spring Boot servisleri için Dockerfile yok — Jib otomatik container'a paketler:

```bash
cd backend
mvn -B compile jib:dockerBuild -DskipTests \
  -pl eureka-server,config-server,api-gateway,user-service,product-service,cart-service,order-service,stock-service,notification-service,chat-service
```

### Adım 2 — Tüm sistemi başlat

Frontend image'ı compose tarafından otomatik build edilir (multi-stage Node + Nginx):

```bash
cd ..
docker-compose -f docker-compose.yml -f docker-compose.full.yml up -d --build
```

İlk başlatmada ~2-3 dakika sürer.

### Adım 3 — Sistem durumunu kontrol et

```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml ps
```

### Adım 4 — Erişim noktaları

| URL | Açıklama |
|---|---|
| http://localhost:3000 | Frontend (React + Nginx) |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:15672 | RabbitMQ Management (guest/guest) |
| http://localhost:8090 | Keycloak Admin Console |
| http://localhost:8087/swagger-ui.html | Chat Service API |

### Sistemi durdur

```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml down
```

Verileri silmek için `-v` ekle:
```bash
docker-compose -f docker-compose.yml -f docker-compose.full.yml down -v
```

---

## ⚙️ CI/CD — GitHub Actions

Her push'ta otomatik çalışır:

1. **Test** — Postgres + RabbitMQ + Redis container'ları ile tüm unit testler
2. **Verify Jib Build** — Tüm servisler matrix build ile container image olarak doğrulanır
3. **Publish** (sadece main) — Container image'lar registry'e push edilir
4. **Notify** — Sonuç Slack'e bildirilir

Detaylar: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

---

## 🧪 Testler

```bash
# Tüm testler
cd backend
mvn test

# Belirli servis
mvn -pl order-service test

# Spesifik test class
mvn -pl order-service test -Dtest=OrderServiceIdempotencyTest
```

**Test türleri:**
- **Unit testler** — JUnit 5 + Mockito + AssertJ ile servis ve mapper katmanları
- **Integration testler** — Testcontainers ile Postgres, Redis, RabbitMQ kullanılır
- **Kritik akışlar** — Idempotency, Outbox publishing, Stock concurrency, Order status machine

---

## 📡 API Örnekleri

### 1. Login (Keycloak'tan token al)

```bash
curl -X POST http://localhost:8090/realms/ecommerce/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=ecommerce-app&username=testuser&password=test123"
```

### 2. Sipariş oluştur (Idempotency-Key ile)

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

### 3. AI ile ürün ara

```bash
curl -X POST http://localhost:8080/api/v1/chat/search \
  -H "Content-Type: application/json" \
  -H "Accept-Language: tr" \
  -d '{"query": "10000 tl altı kulaklık"}'
```

**Iyzico test kartları:**
- ✅ Başarılı: `5528 7900 0000 0008` (Master) | `4543 5908 8200 9486` (Visa)
- ❌ Başarısız: `4111 1111 1111 1129`
- Ortak: SKT `12/30`, CVC `123`

---

## 📊 Mimari Kararlar

### "Neden Choreography Saga?"
Servis sayısı az (6 mikroservis), orchestrator overhead'i istenmedi. Coupling de düşük olur. Trade-off: akışı izlemek için distributed tracing (Sleuth/Zipkin) eklenebilir.

### "Outbox Pattern neden gerekli?"
Order DB'ye yazılıp ardından RabbitMQ publish başarısız olursa: order var, ama event yok → inconsistent state. Outbox bu sorunu **DB transaction garantisi** ile çözer. RabbitMQ sonra ne olursa olsun event eninde sonunda publish edilir.

### "İki kişi son ürünü aynı anda alırsa?"
Stock-service `@Lock(PESSIMISTIC_WRITE)` ile çekiyor (`SELECT ... FOR UPDATE`). İlk transaction lock alır, ikinci bekler. Lock release olunca stoğun yetersiz olduğunu görür → StockRejectedEvent.

### "Idempotency neden önemli?"
Kullanıcı submit'e iki kez basabilir, network glitch retry yapabilir, mobile app double-tap olabilir. Idempotency-Key UNIQUE constraint sayesinde DB seviyesinde duplicate engellenir. Stripe da aynısını yapıyor.

### "Multi-instance Outbox publisher çakışmaz mı?"
`SELECT ... FOR UPDATE SKIP LOCKED` (Postgres'in özelliği) kullanılıyor. Bir publisher kilitlediği satırı diğeri SKIP eder, duplicate publish olmaz.

### "PCI-DSS uyumu nasıl?"
Kart numarası DB'ye **hiç** yazılmıyor. Iyzico'ya direkt iletiliyor, sadece paymentId (token) saklanıyor. Saga akışı sırasında 15dk Redis TTL ile geçici cache var, sonra otomatik silinir.

### "AI çağrısı her sorguda yapılıyor mu?"
Groq çağrısı her sorguda gider (LLM doğal dil anlama için). Ancak kategori/marka metadata'sı Caffeine cache ile 10dk tutulur — her LLM çağrısı için product-service'e gitmek gerekmez.

---

## 📚 Daha Fazla Doküman

- [`docs/saga-flow.md`](docs/saga-flow.md) — Detaylı sequence diagram
- [`docs/adr/`](docs/adr/) — Architecture Decision Records
- [`docs/api/`](docs/api/) — Servis bazlı Swagger dokümantasyonları
- [`docs/postman/`](docs/postman/) — Postman collection

---

## 🛠️ Teknoloji Stack'i

**Backend:** Spring Boot 3.4 · Spring Cloud 2024 · Spring Security · Spring Data JPA · OpenFeign · Resilience4j · RabbitMQ · Redis · PostgreSQL · Keycloak · Iyzico SDK · Caffeine · Lombok · MapStruct · JUnit 5 · Mockito · Testcontainers · Springdoc OpenAPI

**Frontend:** React 18 · TypeScript · Vite · TanStack Query · Zustand · React Router · Tailwind CSS · Lucide Icons · Axios · STOMP.js

**DevOps:** Docker · Docker Compose · Jib · Nginx · GitHub Actions · Slack Webhook

**AI:** Groq (`llama-3.1-8b-instant`)

---

## 👤 Geliştirici

**Aslı Durucan**
N11 TalentHub Backend Bootcamp (patika.dev × n11) · 2026

---
