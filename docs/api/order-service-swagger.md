# Order Service — API Dokümantasyonu

Sipariş yönetimi mikroservisi. Sepet → sipariş dönüşümü, Iyzico ödeme entegrasyonu, **Saga choreography** ile dağıtık transaction yönetimi sağlar.

---

## 📍 Erişim

| Ortam | URL |
|---|---|
| Swagger UI | http://localhost:8084/swagger-ui.html |
| OpenAPI JSON | http://localhost:8084/v3/api-docs |
| Gateway üzerinden (dış erişim) | http://localhost:8080/api/orders |

---

## 🏗️ Mimari Notlar

### Saga Choreography Pattern

Sipariş akışı tek bir merkezi orkestratör değil, event'ler üzerinden ilerler. Her servis kendi sorumluluğunda olan event'i dinler ve sonucu tekrar event olarak yayınlar.

**Happy Path:**
```
1. POST /api/orders
   └─> Order PENDING + Outbox event yazılır

2. OrderCreated event yayınlanır (RabbitMQ)
   └─> stock-service dinler → Reserve → StockReserved event

3. StockReserved event geri gelir
   └─> Order STOCK_RESERVED → PAYMENT_PROCESSING

4. Iyzico'ya ödeme isteği
   └─> Başarılı → Order COMPLETED + OrderCompleted event

5. OrderCompleted event yayınlanır
   └─> stock-service dinler → reserved → committed (kalıcı düşüş)
```

**Failure Path (compensation):**
```
- Stok yetersiz → StockRejected event → Order CANCELLED
- Ödeme reddedilirse → PaymentFailed event → stock-service stoğu geri açar
```

### Outbox Pattern

Order DB transaction'ı ile event yayınlanması atomik olarak ayrılamaz, bu yüzden event'ler önce DB'ye yazılır (`outbox_event` tablosu), sonra polling-based publisher (default 2 saniyede bir) RabbitMQ'ya yayınlar. Bu, "DB commit oldu ama event gitmedi" senaryosunu önler.

### Idempotency

`Idempotency-Key` header zorunlu — aynı anahtarla iki kez POST atılırsa yeni sipariş oluşmaz, ilkinin response'u döner. Network retry, double-click, vs. için kritik.

---

## 🔐 Authentication

Tüm endpoint'ler Keycloak JWT token gerektirir. **Admin endpoint'leri** ek olarak `client_admin` rolü ister.

### Token alma

```bash
curl -X POST "http://localhost:8090/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=new-user-service&username=test@test.com&password=test123"
```

> Windows CMD için tek satıra al, `\` kaldır.

| Username | Şifre | Roller |
|---|---|---|
| `admin` | `admin123` | `client_admin`, `musteri` |
| `test@test.com` | `test123` | `musteri` |

Token süresi: 30 dakika.

---

## ⚠️ Önemli — Header Gereksinimleri

Bu servis Gateway'in inject ettiği header'ları bekler. Production akışta frontend → Gateway → order-service zincirinde Gateway bu header'ları otomatik koyar. Ama Swagger UI'dan **doğrudan order-service'e** istek atarken header'ları manuel doldurmak gerekir.

| Header | Kaynak | Zorunlu | Açıklama |
|---|---|---|---|
| `Authorization` | Frontend | ✅ | `Bearer {token}` |
| `X-User-Id` | Gateway (JWT `sub`) | ✅ | Kullanıcı UUID'si |
| `X-User-Username` | Gateway (JWT `preferred_username`) | ❌ | Audit için |
| `Idempotency-Key` | Frontend (UUID) | ✅ (POST) | Tekrar koruması |

> Swagger UI'dan test ederken `X-User-Id` değerini token'ın `sub` claim'inden alın. Token'ı [jwt.io](https://jwt.io)'ya yapıştırın, payload'daki `sub` alanını kopyalayın.

---

## 📚 Endpoint'ler

### `POST /api/orders` — Sipariş oluştur

Yeni sipariş oluşturur, ödemeyi başlatır. Saga akışı bu çağrıyla tetiklenir.

**Request body:**
```json
{
  "items": [
    {
      "productId": 3,
      "quantity": 1,
      "unitPrice": 79999.99,
      "productName": "MacBook Pro M3 14\""
    }
  ],
  "shippingAddress": {
    "firstName": "Aslı",
    "lastName": "Durucan",
    "email": "test@test.com",
    "phone": "+905551234567",
    "address": "Atatürk Mah. Cumhuriyet Cad. No:1 D:5",
    "city": "Konya",
    "country": "TR"
  },
  "card": {
    "holderName": "Asli Durucan",
    "number": "5526080000000006",
    "expireMonth": "12",
    "expireYear": "2030",
    "cvc": "123"
  }
}
```

**Iyzico Sandbox Test Kartları:**

| Kart No | Senaryo |
|---|---|
| `5526080000000006` | Mastercard, başarılı ödeme |
| `4543600299100008` | Visa, 3D Secure başarılı |
| `4111111111111129` | Reddedilir (insufficient funds) |

**Yanıtlar:**
- `201 Created` — Sipariş oluştu, status `PENDING` (Saga başladı)
- `200 OK` — Aynı Idempotency-Key ile tekrar denendi, mevcut sipariş döner
- `400 Bad Request` — Validasyon hatası (eksik alan, geçersiz kart formatı)
- `401 Unauthorized` — Token yok veya `X-User-Id` eksik
- `500 Internal Server Error` — Saga başlangıç hatası

**Örnek response (201):**
```json
{
  "id": 19,
  "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "idempotencyKey": "a3f1c8e0-1234-4abc-9def-567890123456",
  "status": "PENDING",
  "totalAmount": 79999.99,
  "currency": "TRY",
  "items": [...],
  "createdAt": "2026-05-02T10:30:00Z"
}
```

> `status` alanı zaman içinde değişir (asenkron Saga). Birkaç saniye sonra tekrar `GET /api/orders/{id}` ile sorgulayarak nihai durumu (`COMPLETED` veya `CANCELLED`) görmek gerekir.

---

### `GET /api/orders/me` — Kendi siparişlerim

Token sahibi kullanıcının siparişlerini sayfalı olarak döner.

**Query parametreleri:**
- `page` (default `0`)
- `size` (default `20`)
- `sort` (default `createdAt,desc`)

**Header:** `X-User-Id` zorunlu.

**Yanıtlar:**
- `200 OK` — Sayfalı sipariş listesi (boş olabilir)
- `401 Unauthorized` — Token yok / X-User-Id eksik

---

### `GET /api/orders/{id}` — Sipariş detayı

Tek bir siparişin detayını döner. Kullanıcı sadece **kendi** siparişine erişebilir; başka kullanıcının siparişine erişim 403 döner.

**Yanıtlar:**
- `200 OK` — Sipariş detayı
- `403 Forbidden` — Başkasının siparişi
- `404 Not Found` — Sipariş yok

---

### `GET /api/orders` — Tüm siparişler — Admin

Sistemdeki tüm siparişleri sayfalı olarak döner. **`client_admin` rolü gerekir.**

**Yanıtlar:**
- `200 OK` — Tüm siparişler
- `403 Forbidden` — Admin değil

---

## 🔄 Sipariş Status Akışı

```
PENDING ──> STOCK_RESERVED ──> PAYMENT_PROCESSING ──> COMPLETED
   │              │                      │
   │              ↓                      ↓
   └─────────> CANCELLED <───────────────┘
            (insufficient stock        (payment failed,
             or compensation)           compensation triggered)
```

| Status | Anlam |
|---|---|
| `PENDING` | Sipariş oluştu, stok rezervasyonu bekleniyor |
| `STOCK_RESERVED` | Stok ayrıldı, ödeme başlıyor |
| `PAYMENT_PROCESSING` | Iyzico'ya ödeme isteği gönderildi |
| `COMPLETED` | Ödeme onaylandı, stok kalıcı olarak düştü |
| `CANCELLED` | Stok yetersiz veya ödeme başarısız (compensation çalıştı) |

---

## 🧪 Test Adımları (Manuel Doğrulama)

### Happy Path
1. **Token al** — `test@test.com / test123`
2. **`X-User-Id`** — token'ın `sub` claim'i
3. **POST** — yeni Idempotency-Key ile sipariş oluştur → `201 PENDING`
4. **2-3 saniye bekle** — Saga akışının tamamlanması için
5. **GET /api/orders/{id}** — status `COMPLETED` olmalı
6. **Stoğu doğrula** — `GET /api/stocks/{productId}`'da `availableQuantity` 1 düşmüş, `reservedQuantity: 0` olmalı

### Idempotency
7. Aynı body + **aynı Idempotency-Key** ile POST tekrar → aynı `id` döner, yeni sipariş oluşmaz

### Failure Path (stok yetersizliği)
8. Stoğu 0'a çek (`PUT /api/stocks` admin token ile)
9. Yeni Idempotency-Key ile POST → 5 saniye sonra `GET` → status `CANCELLED`

### Authorization
10. `test@test.com` token'ı ile `GET /api/orders` → **403 Forbidden**
11. `admin` token'ı ile `GET /api/orders` → **200 OK**, tüm siparişler

---

## 🛡️ Güvenlik Notları

- Kullanıcı kendi siparişlerinden başkasını **göremez** (controller-level kontrol + service-level filter)
- `X-User-Id` header **trusted source'dan** (Gateway) gelmeli — production'da Gateway dışından gelen istekler bu header'ı reddetmeli
- Iyzico API anahtarları environment variable olarak yönetilir (`IYZICO_API_KEY`, `IYZICO_SECRET_KEY`)
- `payment.mock` flag'i ile mock mode'a geçiş yapılabilir (test/dev için)
- Resilience4j Circuit Breaker Iyzico erişiminde uygulanır

---

## 🔗 Servis Bağımlılıkları

| Bağımlılık | Kullanım |
|---|---|
| **PostgreSQL** | Sipariş, sipariş kalemleri, outbox tablosu |
| **RabbitMQ** | Saga event bus |
| **Iyzico Sandbox** | Ödeme alımı |
| **stock-service** | Stok rezervasyonu (asenkron, event üzerinden) |
| **Keycloak** | JWT validation |

> Order-service'i tek başına ayağa kaldırırsanız PENDING'den ileri gitmez — stock-service ve RabbitMQ'nun da ayakta olması gerekir.

---

## 🔗 İlgili Dosyalar

- Controller: `order-service/src/main/java/com/n11bootcamp/order/controller/OrderController.java`
- Service: `order-service/src/main/java/com/n11bootcamp/order/service/OrderServiceImpl.java`
- Saga listener: `order-service/src/main/java/com/n11bootcamp/order/messaging/OrderSagaListener.java`
- Outbox publisher: `order-service/src/main/java/com/n11bootcamp/order/service/OutboxPublisher.java`
- Iyzico integration: `order-service/src/main/java/com/n11bootcamp/order/service/payment/IyzicoPaymentServiceImpl.java`
- OpenAPI config: `order-service/src/main/java/com/n11bootcamp/order/config/OpenApiConfig.java`
- Security config: `order-service/src/main/java/com/n11bootcamp/order/config/SecurityConfig.java`

> Saga akışının detaylı diagramı için: [`docs/saga-flow.md`](../saga-flow.md)