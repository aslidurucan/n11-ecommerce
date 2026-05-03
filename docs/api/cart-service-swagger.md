# Cart Service — API Dokümantasyonu

Sepet yönetimi mikroservisi. Sepete ekleme, miktar güncelleme, çıkarma ve tamamen boşaltma işlemlerini sağlar.

---

## 📍 Erişim

| Ortam | URL |
|---|---|
| Swagger UI | http://localhost:8083/swagger-ui.html |
| OpenAPI JSON | http://localhost:8083/v3/api-docs |
| Gateway üzerinden (dış erişim) | http://localhost:8080/api/cart |

---

## 🏗️ Mimari Notu

Sepet işlemleri **kullanıcı bazlıdır** — her endpoint JWT'deki `sub` claim'ini kullanıcı kimliği olarak alır. Bir kullanıcı yalnızca kendi sepetine erişebilir; kullanıcı id'si request body'de veya path'te gönderilmez, otomatik olarak token'dan çıkarılır.

Cart-service ürün doğrulaması ve fiyat hesaplaması için **product-service**'i Feign Client üzerinden çağırır:

```
Cart  ──Feign──>  product-service (Eureka discovery + lb://)
```

Bu nedenle cart endpoint'lerini test ederken product-service'in de ayakta olması gerekir.

---

## 🔐 Authentication

**Tüm endpoint'ler** Keycloak JWT token gerektirir. Public endpoint yoktur.

### Token alma

```bash
curl -X POST "http://localhost:8090/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=new-user-service&username=test@test.com&password=test123"
```

> Windows CMD için tek satıra al, `\` kaldır.

> **Önemli:** Realm'da kullanıcılar **email-as-username** modunda. Username olarak email adresinin tamamı kullanılır (`test@test.com`), sadece local kısmı (`testuser`) değil.

Yanıttaki `access_token` değerini Swagger UI'daki **Authorize** butonuna `Bearer ` öneki olmadan yapıştır.

| Username (email) | Şifre | Roller |
|---|---|---|
| `admin` | `admin123` | `client_admin`, `musteri` |
| `test@test.com` | `test123` | `musteri` |

> Admin user'ı email yerine `admin` ile login olur (email yerine direkt username kullanan tek kullanıcı). Diğer kullanıcılar için email kullan.

Token süresi: 30 dakika.

---

## 📚 Endpoint'ler

### `GET /api/cart` — Sepetimi getir

Token sahibi kullanıcının mevcut sepetini döner. Sepet yoksa otomatik olarak boş sepet oluşturulur (lazy initialization).

**Yanıtlar:**
- `200 OK` — Sepet (boş ya da dolu)
- `401 Unauthorized` — Token yok / geçersiz

**Örnek yanıt (boş sepet):**
```json
{
  "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "items": [],
  "itemCount": 0,
  "grandTotal": 0,
  "updatedAt": "2026-05-02T10:15:30.123456Z"
}
```

**Örnek yanıt (dolu sepet):**
```json
{
  "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "items": [
    {
      "productId": 3,
      "productName": "MacBook Pro M3 14\"",
      "unitPrice": 79999.99,
      "quantity": 2,
      "lineTotal": 159999.98
    }
  ],
  "itemCount": 1,
  "grandTotal": 159999.98,
  "updatedAt": "2026-05-02T10:20:45.123456Z"
}
```

> `itemCount` sepetteki **farklı ürün satırı** sayısıdır, toplam adet değil.

---

### `POST /api/cart/items` — Sepete ürün ekle

Sepete yeni bir ürün ekler. Aynı ürün zaten sepetteyse adet artar (servis logic'ine göre).

**Header:** `Accept-Language: tr|en` (opsiyonel — ürün ismi/açıklamasının dili için)

**Request body:**
```json
{
  "productId": 3,
  "quantity": 2
}
```

**Validasyon kuralları:**
- `productId` zorunlu
- `quantity` zorunlu ve pozitif (1 veya daha büyük)

**İş akışı:**
1. Cart-service Feign Client ile product-service'e gider, ürünü doğrular ve fiyat/isim alır
2. Ürün varsa sepete eklenir, yoksa 404 döner
3. Güncellenmiş sepet response olarak döner

**Yanıtlar:**
- `200 OK` — Güncellenmiş sepet döner
- `400 Bad Request` — Validasyon hatası (productId null, quantity ≤ 0)
- `401 Unauthorized` — Token yok / geçersiz
- `404 Not Found` — Ürün bulunamadı (product-service'ten gelen)
- `500 Internal Server Error` — Product-service erişilemiyor (downstream hata)

---

### `PATCH /api/cart/items/{productId}` — Ürün adedini güncelle

Sepetteki bir ürünün adedini değiştirir. Yalnızca quantity güncellenir, diğer alanlar değişmez.

**Path parametresi:** `productId` (Long)

**Request body:**
```json
{
  "quantity": 5
}
```

**Validasyon kuralları:**
- `quantity` zorunlu ve pozitif

**Yanıtlar:**
- `200 OK` — Güncellenmiş sepet döner
- `400 Bad Request` — Validasyon hatası
- `404 Not Found` — Ürün sepette yok

> Quantity'yi 0 yapmak istiyorsan `DELETE /api/cart/items/{productId}` kullan.

---

### `DELETE /api/cart/items/{productId}` — Ürünü sepetten çıkar

Belirtilen ürünü sepetten tamamen çıkarır.

**Yanıtlar:**
- `204 No Content` — Çıkarıldı
- `404 Not Found` — Ürün sepette zaten yok

---

### `DELETE /api/cart` — Sepeti tamamen boşalt

Tüm item'ları siler. Sepet objesinin kendisi kalır ama içi boş olur.

**Yanıtlar:**
- `204 No Content` — Sepet boşaltıldı

> Bu endpoint genellikle sipariş başarılı şekilde tamamlandıktan sonra çağrılır (Saga akışında).

---

## 🧪 Test Adımları (Manuel Doğrulama)

Aşağıdaki sıra Swagger UI üzerinden tüm sepet akışını doğrular:

1. **Token al** — `test@test.com / test123` ile, Authorize'a yapıştır
2. **Boş sepet:** `GET /api/cart` → 200, items boş
3. **Ürün ekle:** `POST /api/cart/items` (geçerli `productId` ile, product-service'ten al) → 200
4. **Aynı ürünü tekrar ekle** → quantity artmalı
5. **Farklı ürün ekle** → 2. satır oluşmalı, `itemCount: 2`
6. **Quantity güncelle:** `PATCH /api/cart/items/{productId}` → 200, yeni miktar
7. **Ürün çıkar:** `DELETE /api/cart/items/{productId}` → 204
8. **Doğrula:** `GET /api/cart` → ürün yok, diğeri kalmış
9. **Sepeti boşalt:** `DELETE /api/cart` → 204
10. **Doğrula:** `GET /api/cart` → boş sepet

---

## 🛡️ Güvenlik Notları

- Tüm endpoint'ler `@AuthenticationPrincipal Jwt jwt` ile token sahibinden user id alır
- Bir kullanıcı başka kullanıcının sepetine **hiçbir koşulda** erişemez (path veya body üzerinden user id geçilemez)
- Stateless session policy uygulanır — her istek tek başına token ile doğrulanır
- Swagger UI ve OpenAPI endpoint'leri whitelist'tedir, auth gerektirmez

---

## 🔗 Servis Bağımlılıkları

| Bağımlılık | Kullanım |
|---|---|
| **product-service** | Ürün doğrulama, fiyat ve isim alma (Feign + Eureka) |
| **PostgreSQL** | Sepet ve sepet item'larının kalıcı depolanması |
| **Keycloak** | JWT validation |

> Cart-service'i ayrı çalıştırırken product-service ve Eureka'nın da ayakta olması gerekir, aksi halde POST/PATCH 500 dönebilir.

---

## 🔗 İlgili Dosyalar

- Controller: `cart-service/src/main/java/com/n11bootcamp/cart/controller/CartController.java`
- Service: `cart-service/src/main/java/com/n11bootcamp/cart/service/CartService.java`
- Feign client: `cart-service/src/main/java/com/n11bootcamp/cart/client/ProductClient.java`
- OpenAPI config: `cart-service/src/main/java/com/n11bootcamp/cart/config/OpenApiConfig.java`
- Security config: `cart-service/src/main/java/com/n11bootcamp/cart/config/SecurityConfig.java`