# Stock Service — API Dokümantasyonu

Envanter ve stok yönetimi mikroservisi. Saga akışında **stok rezervasyon yaşam döngüsünü** yönetir: reserve → commit (başarı) veya reserve → release (başarısızlık, compensation).

---

## 📍 Erişim

| Ortam | URL |
|---|---|
| Swagger UI | http://localhost:8085/swagger-ui.html |
| OpenAPI JSON | http://localhost:8085/v3/api-docs |
| Gateway üzerinden (dış erişim) | http://localhost:8080/api/stocks |

---

## 🏗️ Mimari Notlar

### İki Sayaçlı Stok Modeli

Stoğun gerçek zamanlı durumunu yansıtmak için iki kolon tutulur:

| Kolon | Anlam |
|---|---|
| `availableQuantity` | Sipariş için ayrılabilir, satılabilir adet |
| `reservedQuantity` | Sipariş başladı, ödeme bekleniyor — geri açılabilir |

**Saga akışında ne olur:**

```
Başlangıç:        available=100, reserved=0

reserveStock:     available=99,  reserved=1   ← müşteri sipariş verdi
   │
   ├─ Ödeme OK ──> commitReservation:
   │                available=99,  reserved=0   ← kalıcı tüketildi
   │
   └─ Ödeme FAIL ─> releaseStock (compensation):
                    available=100, reserved=0   ← geri serbest
```

Bu model "ödeme bekleyen sipariş" durumunu da modellemeye izin verir — başka bir müşteri aynı ürünü almaya çalışırsa rezerve edilmiş adetler "available" değildir.

### Saga Listener'ları

Stock-service üç farklı event'i dinler:

| Event | Action | Sonuç |
|---|---|---|
| `OrderCreated` | reserveStock | available ↓, reserved ↑ |
| `OrderCompleted` | commitReservation | reserved ↓ (sıfırlanır) |
| `PaymentFailed` | releaseStock | available ↑, reserved ↓ |

### Concurrency

Stok güncellemeleri **pessimistic locking** (`SELECT ... FOR UPDATE`) ile yapılır. Aynı anda iki kullanıcı son adetli ürünü almaya çalışırsa biri bekler, diğeri yetersiz stok hatası alır. Race condition'a kapalı.

İdempotency: `stock_reservations` tablosu order_id ile rezervasyonu takip eder. Aynı `OrderCreated` event'i iki kez gelirse ikincisi tespit edilip atılır.

---

## 🔐 Authentication

| Endpoint | Auth |
|---|---|
| `GET /api/stocks/{productId}` | Public — token gerekmez |
| `PUT /api/stocks` | `client_admin` rolü |
| `PATCH /api/stocks/{productId}/increase` | `client_admin` rolü |
| `PATCH /api/stocks/{productId}/decrease` | `client_admin` rolü |

### Token alma (admin için)

```bash
curl -X POST "http://localhost:8090/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=new-user-service&username=admin&password=admin123"
```

| Username | Şifre | Roller |
|---|---|---|
| `admin` | `admin123` | `client_admin`, `musteri` |
| `test@test.com` | `test123` | `musteri` |

---

## 📚 Endpoint'ler

### `GET /api/stocks/{productId}` — Stok sorgula

Bir ürünün güncel stok durumunu döner. Public erişim.

Redis cache'lenir (default 5 dakika TTL). Stok yazma işlemleri cache'i temizler.

**Yanıtlar:**
- `200 OK` — Stok bilgisi
- `404 Not Found` — Stok kaydı yok

**Örnek yanıt:**
```json
{
  "productId": 3,
  "availableQuantity": 99,
  "reservedQuantity": 1
}
```

---

### `PUT /api/stocks` — Stok set et

Bir ürünün stoğunu **mutlak** olarak ayarlar (mevcut değer ne olursa olsun, gönderilen değere set eder). İlk seed veya envanter düzeltmesi için.

**`client_admin` rolü gerekir.**

**Request body:**
```json
{
  "productId": 3,
  "quantity": 100
}
```

**Validasyon:**
- `productId` zorunlu
- `quantity` 0 veya pozitif (negatif olmaz)

**Yanıtlar:**
- `200 OK` — Güncel stok bilgisi
- `400 Bad Request` — Validasyon hatası
- `403 Forbidden` — Admin değil

> Bu endpoint `reservedQuantity`'i **etkilemez**, sadece `availableQuantity`'i set eder. Aktif rezervasyonlar varsa o değer korunur.

---

### `PATCH /api/stocks/{productId}/increase?delta=N` — Stok arttır

Stoğa adet ekler (depo girişi, iade, vb.).

**`client_admin` rolü gerekir.**

**Query parametre:**
- `delta` (int, zorunlu, pozitif olmalı) — eklenecek miktar

**Örnek:** `PATCH /api/stocks/3/increase?delta=10` → mevcut + 10

**Yanıtlar:**
- `200 OK` — Güncel stok bilgisi
- `400 Bad Request` — `delta` ≤ 0
- `404 Not Found` — Stok kaydı yok

---

### `PATCH /api/stocks/{productId}/decrease?delta=N` — Stok azalt

Stoktan adet düşer (fire, kayıp, manuel düzeltme).

**`client_admin` rolü gerekir.**

> Bu endpoint **sipariş akışıyla ilgili değil** — sipariş tüketimi otomatik olarak `OrderCompleted` event'iyle yapılır. Bu sadece manuel envanter düzeltmesi içindir.

**Query parametre:**
- `delta` (int, zorunlu, pozitif olmalı, mevcut stoktan az olmalı)

**Yanıtlar:**
- `200 OK` — Güncel stok bilgisi
- `400 Bad Request` — `delta` ≤ 0 veya yetersiz stok

---

## 🔁 Saga'da Stock-Service'in Rolü (REST'ten Bağımsız)

Yukarıdaki REST endpoint'leri **manuel envanter yönetimi** içindir. Sipariş akışı asenkron olarak event üzerinden çalışır:

| Adım | Event (gelen) | Servis metodu | Stok değişimi |
|---|---|---|---|
| 1 | `OrderCreated` | `reserveStock(orderId, items)` | available ↓, reserved ↑ |
| 2 | (event yayınla) | — | `StockReserved` veya `StockRejected` |
| 3a | `OrderCompleted` | `commitReservation(orderId)` | reserved ↓ |
| 3b | `PaymentFailed` | `releaseStock(orderId)` | available ↑, reserved ↓ |

Bu metodlar HTTP üzerinden değil, RabbitMQ listener üzerinden tetiklenir. Saga'nın asıl değeri buradadır.

---

## 🧪 Test Adımları (Manuel Doğrulama)

### REST Endpoint'leri

1. **Public okuma:** `GET /api/stocks/3` → 200, stok bilgisi
2. **Token al:** admin user
3. **Set:** `PUT /api/stocks` body `{productId:3, quantity:100}` → 200, available=100
4. **Increase:** `PATCH /api/stocks/3/increase?delta=10` → available=110
5. **Decrease:** `PATCH /api/stocks/3/decrease?delta=15` → available=95
6. **404:** `GET /api/stocks/9999` → 404 (var olmayan ürün)
7. **Yetkisiz:** `test@test.com` token'ı ile `PUT` → 403

### Saga Akışı (order-service üzerinden)

8. Stoğu 100 yap
9. Order-service'te yeni sipariş oluştur (`POST /api/orders`)
10. **Birkaç saniye bekle**
11. `GET /api/stocks/3` → available=99, reserved=0 (commit çalıştı) — happy path
12. Stoğu 0'a çek, yeni sipariş oluştur → CANCELLED, `GET /api/stocks/3` → available=0, reserved=0 (release çalıştı) — failure path

---

## 🛡️ Güvenlik Notları

- GET endpoint'i public (ürün vitrini için yaygın gereksinim)
- Yazma operasyonları sadece admin
- Pessimistic lock + `@Version` (optimistic) çift güvence ile concurrent update korumalı
- Saga listener'ları `@Transactional` — DB commit + RabbitMQ publish zinciri en az 2-aşamalı, tam atomic değil ama "at-least-once" semantiği var (idempotency ile birleşince güvenli)

---

## 🔗 Servis Bağımlılıkları

| Bağımlılık | Kullanım |
|---|---|
| **PostgreSQL** | `product_stocks`, `stock_reservations` tabloları |
| **Redis** | Stok sorgu cache (5 dk TTL) |
| **RabbitMQ** | Saga event subscribers |
| **Keycloak** | JWT validation (yazma endpoint'leri için) |

---

## 🔗 İlgili Dosyalar

- Controller: `stock-service/src/main/java/com/n11bootcamp/stock/controller/StockController.java`
- Service: `stock-service/src/main/java/com/n11bootcamp/stock/service/StockServiceImpl.java`
- Entity: `stock-service/src/main/java/com/n11bootcamp/stock/entity/ProductStock.java`
- Saga listener: `stock-service/src/main/java/com/n11bootcamp/stock/messaging/StockSagaListener.java`
- RabbitMQ config: `stock-service/src/main/java/com/n11bootcamp/stock/config/RabbitMQConfig.java`
- OpenAPI config: `stock-service/src/main/java/com/n11bootcamp/stock/config/OpenApiConfig.java`
- Security config: `stock-service/src/main/java/com/n11bootcamp/stock/config/SecurityConfig.java`