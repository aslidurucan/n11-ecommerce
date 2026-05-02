package com.n11bootcamp.cart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI cartServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cart Service API")
                        .description("""
                                n11 E-Commerce — Sepet yönetimi mikroservisi.

                                Sepet işlemleri kullanıcı bazlıdır — her endpoint JWT'deki `sub` claim'ini
                                kullanıcı kimliği olarak kullanır. Bir kullanıcı yalnızca kendi sepetine erişebilir.

                                ## Authentication
                                Tüm endpoint'ler Keycloak JWT token gerektirir.
                                Sağ üstteki **Authorize** butonuna token'ı `Bearer ` öneki olmadan yapıştırın.

                                ### Token alma
                                ```
                                POST http://localhost:8090/realms/ecommerce/protocol/openid-connect/token
                                Content-Type: application/x-www-form-urlencoded

                                grant_type=password&client_id=new-user-service&username=testuser&password=test123
                                ```

                                ### Test kullanıcıları
                                | Username | Password | Roller |
                                |---|---|---|
                                | `test@test.com` | `test123` | musteri |
                                | `admin` | `admin123` | client_admin, musteri |
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("n11 Bootcamp")
                                .email("aslidurucan@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak'tan alınan access_token. 'Bearer ' öneki olmadan yapıştırın.")));
    }
}