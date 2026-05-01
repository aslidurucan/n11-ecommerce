-- Keycloak kullanıcı bilgilerini (ad, email, şifre, roller) depolar.
-- Biz sadece Keycloak'ın doğrudan desteklemediği ek alanları tutarız.
CREATE TABLE user_profiles (
    keycloak_id  VARCHAR(36) PRIMARY KEY,   -- Keycloak'ın sub UUID'si
    phone        VARCHAR(20),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
