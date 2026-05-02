# User Service — API Dokümantasyonu

Kullanıcı yönetimi mikroservisi. Kayıt, profil görüntüleme ve güncelleme işlemlerini sağlar. PostgreSQL + Keycloak çift yönlü senkronize çalışır.

---

## 📍 Erişim

| Ortam | URL |
|---|---|
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Gateway üzerinden (dış erişim) | http://localhost:8080/api/users |

---

## 🏗️ Mimari Notu

Bu servis kullanıcı verisini **iki kaynakta** tutar:

- **Keycloak** — kimlik doğrulama, şifre, roller, JWT issuer
- **PostgreSQL** — uygulama-spesifik profil verisi (telefon, adres, vb.)

`POST /signup` her iki sistemde de kayıt oluşturur (`KeycloakAdminConfig` admin client'ı kullanır). `keycloakId` iki sistem arasında bağlantı kuran foreign key görevini görür.

---

## 🔐 Authentication

`/me` endpoint'leri Keycloak JWT token gerektirir. `/signup` public'tir.

### Token alma

```bash
curl -X POST "http://localhost:8090/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=new-user-service&username=admin&password=admin123"
```

> Windows CMD için tek satıra al, `\` kaldır.

Yanıttaki `access_token` değerini Swagger UI'daki **Authorize** butonuna `Bearer ` öneki olmadan yapıştır.

| Kullanıcı | Şifre | Roller |
|---|---|---|
| `admin` | `admin123` | `client_admin`, `musteri` |
| `test@test.com` | `test123` | `musteri` |

> Yeni signup ettiğin kullanıcılar default `musteri` rolü alır (`KeycloakAdminConfig.default-role` ayarı).

Token süresi: 30 dakika.

---

## 📚 Endpoint'ler

### `POST /api/users/signup` — Kullanıcı kaydı

Yeni kullanıcı oluşturur. **Public** — auth gerekmez.

Hem PostgreSQL'de hem Keycloak'ta kullanıcı oluşturur, `musteri` rolü atar.

**Request body:**
```json
{
  "firstName": "Aslı",
  "lastName": "Durucan",
  "email": "asli.test@example.com",
  "password": "test12345",
  "phone": "+905551234567"
}
```

**Validasyon kuralları:**
- `firstName`, `lastName` boş olamaz
- `email` geçerli email formatında olmalı
- `password` en az 8 karakter
- `phone` opsiyonel

**Yanıtlar:**
- `201 Created` — Kullanıcı oluşturuldu
- `400 Bad Request` — Validasyon hatası (örn. email formatı, şifre kısa)
- `409 Conflict` — Email zaten kayıtlı (Keycloak duplicate)

**Örnek yanıt (201):**
```json
{
  "keycloakId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "firstName": "Aslı",
  "lastName": "Durucan",
  "email": "asli.test@example.com",
  "phone": "+905551234567",
  "createdAt": "2026-05-02T10:15:30.123456Z"
}
```

> `keycloakId` JWT'nin `sub` claim'i ile aynıdır. Diğer servisler kullanıcıyı bu ID üzerinden referans alır.

---

### `GET /api/users/me` — Kendi profilini getir

Token sahibi kullanıcının profil bilgilerini döner. **Token gerekli.**

Hangi kullanıcı? JWT'deki `sub` claim'inden alınır — kullanıcı kendi profilinden başkasının bilgisini göremez.

**Yanıtlar:**
- `200 OK` — Profil döner
- `401 Unauthorized` — Token yok / geçersiz / süresi dolmuş
- `404 Not Found` — Keycloak'ta var ama PostgreSQL'de profil yoksa (data inconsistency)

**Örnek yanıt:**
```json
{
  "keycloakId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "firstName": "Aslı",
  "lastName": "Durucan",
  "email": "asli.test@example.com",
  "phone": "+905551234567",
  "createdAt": "2026-05-02T10:15:30.123456Z"
}
```

---

### `PATCH /api/users/me` — Kendi profilini güncelle

Token sahibi kullanıcının profilini kısmen günceller. **Token gerekli.**

Sadece gönderilen alanlar değişir. Şifre değişikliği bu endpoint kapsamında değildir — Keycloak account console üzerinden yapılır.

**Güncellenebilir alanlar:**
- `firstName`
- `lastName`
- `email` (geçerli email formatında olmalı)
- `phone`

**Örnek — sadece telefon güncelleme:**
```json
{
  "phone": "+905559876543"
}
```

**Örnek — birden fazla alan:**
```json
{
  "firstName": "Aslıhan",
  "phone": "+905559876543"
}
```

**Yanıtlar:**
- `200 OK` — Güncellenmiş profil döner
- `400 Bad Request` — Validasyon hatası (örn. geçersiz email)
- `401 Unauthorized` — Token yok / geçersiz

> Email değiştirilirse Keycloak tarafında da senkronize edilir. Bu durumda yeni email ile tekrar token alınması gerekebilir.

---

## 🧪 Test Adımları (Manuel Doğrulama)

1. **Signup:** `POST /api/users/signup` → 201, response'daki `keycloakId`'yi not et
2. **Token al:** Yeni kullanıcı için curl ile token al (yukarıdaki örnek, `username` ve `password`'ü değiştir)
3. **Authorize:** Token'ı Swagger UI'a yapıştır
4. **Profil oku:** `GET /api/users/me` → 200, signup'taki bilgilerle aynı
5. **Profil güncelle:** `PATCH /api/users/me` → 200, değişiklik yansımış
6. **Doğrulama:** Tekrar `GET /me` → güncellenmiş bilgiler kalıcı
7. **Yetki testi:** Token olmadan `GET /me` → 401
8. **Duplicate test:** Aynı email ile tekrar signup → 409

---

## 🛡️ Güvenlik Notları

- Şifreler hiçbir zaman PostgreSQL'de saklanmaz — sadece Keycloak'ta hash'li olarak tutulur
- `signup` rate limiting önerilir (Gateway tarafında zaten yapılıyor — `RequestRateLimiter` filter)
- JWT validation Keycloak'ın `jwk-set-uri` üzerinden public key ile yapılır
- Kullanıcı sadece kendi profiline erişebilir (`@AuthenticationPrincipal Jwt jwt` üzerinden subject alınır)

---

## 🔗 İlgili Dosyalar

- Controller: `user-service/src/main/java/com/n11bootcamp/user/controller/UserController.java`
- Service: `user-service/src/main/java/com/n11bootcamp/user/service/UserService.java`
- OpenAPI config: `user-service/src/main/java/com/n11bootcamp/user/config/OpenApiConfig.java`
- Security config: `user-service/src/main/java/com/n11bootcamp/user/config/SecurityConfig.java`
- Keycloak admin config: `user-service/src/main/java/com/n11bootcamp/user/config/KeycloakAdminConfig.java`