# Product Service — API Dokümantasyonu

Ürün kataloğu mikroservisi. Ürün listeleme, detay, ekleme, güncelleme ve silme işlemlerini sağlar.

---

## 📍 Erişim

| Ortam | URL |
|---|---|
| Swagger UI | http://localhost:8082/swagger-ui.html |
| OpenAPI JSON | http://localhost:8082/v3/api-docs |
| Gateway üzerinden (dış erişim) | http://localhost:8080/api/products |

> Swagger UI'a doğrudan servis port'undan (`8082`) erişilir. Production'da bu port dışarı açık olmamalı; sadece Gateway (`8080`) public olur.

---

## 🔐 Authentication

Yazma operasyonları (POST/PATCH/DELETE) `CLIENT_ADMIN` rolü gerektirir. Okuma operasyonları (GET) public'tir.

### Token alma

```bash
curl -X POST "http://localhost:8090/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=new-user-service&username=admin&password=admin123"
```

> Windows CMD için aynı komutu tek satırda yaz, `\` kaldır.

Yanıttaki `access_token` değerini Swagger UI'daki **Authorize** butonuna `Bearer ` öneki olmadan yapıştır.

| Kullanıcı | Şifre | Roller |
|---|---|---|
| `admin` | `admin123` | `client_admin`, `musteri` |
| `test@test.com` | `test123` | `musteri` |

Token süresi: 30 dakika. Süresi dolduğunda 401 alırsan token'ı yenile.

---

## 🌐 Çoklu Dil Desteği

Bu servis i18n destekler. İstemci ürün isim ve açıklamalarını `Accept-Language` header'ı ile seçebilir.

```
Accept-Language: tr   (default)
Accept-Language: en
```

Talep edilen dilde çeviri yoksa fallback olarak ilk mevcut çeviri döner.

---

## 📚 Endpoint'ler

### `GET /api/products` — Ürün listesi

Filtreli ve sayfalı ürün listesi döner. Public.

**Query parametreleri:**

| Parametre | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `category` | string | hayır | Kategori adı (örn. `Bilgisayar`) |
| `brand` | string | hayır | Marka adı (örn. `Apple`) |
| `minPrice` | decimal | hayır | Alt fiyat sınırı |
| `maxPrice` | decimal | hayır | Üst fiyat sınırı |
| `page` | int | hayır | Sayfa numarası (default `0`) |
| `size` | int | hayır | Sayfa boyutu (default `20`) |
| `sort` | string | hayır | Sıralama alanı (default `createdAt`) |

**Örnek istek:**
```
GET /api/products?category=Bilgisayar&minPrice=50000&page=0&size=10&sort=basePrice,asc
```

**Örnek yanıt (200 OK):**
```json
{
  "content": [
    {
      "id": 3,
      "name": "MacBook Pro M3 14\"",
      "description": "Apple M3 çip, Liquid Retina XDR ekran...",
      "category": "Bilgisayar",
      "brand": "Apple",
      "basePrice": 79999.99,
      "imageUrl": "https://...",
      "active": true,
      "createdAt": "2026-05-01T06:58:46.310150Z"
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 10
}
```

---

### `GET /api/products/{id}` — Ürün detayı

Tek bir ürünün detayını döner. Public.

**Path parametresi:** `id` (Long) — ürün ID

**Header:** `Accept-Language: tr|en` (opsiyonel)

**Yanıtlar:**
- `200 OK` — Ürün bulundu
- `404 Not Found` — Ürün yok

**Örnek yanıt:**
```json
{
  "id": 3,
  "name": "MacBook Pro M3 14\"",
  "description": "Apple M3 çip...",
  "category": "Bilgisayar",
  "brand": "Apple",
  "basePrice": 79999.99,
  "imageUrl": "https://...",
  "active": true,
  "createdAt": "2026-05-01T06:58:46.310150Z"
}
```

---

### `POST /api/products` — Ürün ekleme

Yeni ürün oluşturur. **`CLIENT_ADMIN` rolü gerekir.**

**Request body:**
```json
{
  "category": "Bilgisayar",
  "brand": "Lenovo",
  "basePrice": 64999.99,
  "imageUrl": "https://...",
  "translations": [
    {
      "language": "tr",
      "name": "Lenovo ThinkPad X1 Carbon",
      "description": "Intel Core Ultra 7..."
    },
    {
      "language": "en",
      "name": "Lenovo ThinkPad X1 Carbon",
      "description": "Intel Core Ultra 7..."
    }
  ]
}
```

**Validasyon kuralları:**
- `category` boş olamaz
- `basePrice` zorunlu ve pozitif
- `translations` en az bir dil içermeli
- Her translation'da `language` ve `name` zorunlu

**Yanıtlar:**
- `201 Created` — Ürün oluşturuldu, response body'de yeni `id`
- `400 Bad Request` — Validasyon hatası
- `401 Unauthorized` — Token yok / geçersiz
- `403 Forbidden` — Yetkisiz rol

---

### `PATCH /api/products/{id}` — Kısmi güncelleme

Var olan ürünü kısmen günceller. Sadece gönderilen alanlar değişir. **`CLIENT_ADMIN` rolü gerekir.**

**Güncellenebilir alanlar:**
- `category`
- `brand`
- `basePrice` (pozitif olmalı)
- `imageUrl`
- `active` (boolean — pasifleştirme için)

**Örnek — sadece fiyat indirimi:**
```json
{
  "basePrice": 59999.99
}
```

**Örnek — pasifleştirme:**
```json
{
  "active": false
}
```

> Translation güncellemesi bu endpoint kapsamında değildir; ayrı bir endpoint gerekirse genişletilebilir.

**Yanıtlar:**
- `200 OK` — Güncellenmiş ürün döner
- `404 Not Found` — Ürün yok
- `401`, `403` — Yetki sorunları

---

### `DELETE /api/products/{id}` — Ürün silme

Ürünü kalıcı olarak siler. **`CLIENT_ADMIN` rolü gerekir.**

**Yanıtlar:**
- `204 No Content` — Silme başarılı
- `404 Not Found` — Ürün yok
- `401`, `403` — Yetki sorunları

---

## 🧪 Test Adımları (Manuel Doğrulama)

Aşağıdaki sıra Swagger UI üzerinden tüm akışı doğrular:

1. **Public okuma:** `GET /api/products` → 200, sayfalı liste döner
2. **Detay:** `GET /api/products/1` → 200, ürün detayı
3. **404 senaryosu:** `GET /api/products/99999` → 404
4. **Authorize:** Token al, Swagger UI'da Authorize butonuna yapıştır
5. **POST:** Yeni ürün ekle (yukarıdaki örnek body) → 201, yeni `id` döner
6. **PATCH:** Aynı `id` üzerinde fiyat güncelle → 200
7. **DELETE:** Aynı `id`'yi sil → 204
8. **Doğrulama:** Tekrar `GET /api/products/{id}` → 404 (silindi)

---

## 🛡️ Güvenlik Notları

- JWT validation Keycloak'ın `jwk-set-uri` üzerinden public key ile yapılır
- `realm_access.roles` claim'i Spring Security authority'lerine `ROLE_<UPPERCASE>` formatında map edilir
- Method-level security `@PreAuthorize("hasRole('CLIENT_ADMIN')")` ile uygulanır
- Swagger UI ve OpenAPI JSON endpoint'leri whitelist'tedir, auth gerektirmez

---

## 🔗 İlgili Dosyalar

- Controller: `product-service/src/main/java/com/n11bootcamp/product/controller/ProductController.java`
- OpenAPI config: `product-service/src/main/java/com/n11bootcamp/product/config/OpenApiConfig.java`
- Security config: `product-service/src/main/java/com/n11bootcamp/product/config/SecurityConfig.java`