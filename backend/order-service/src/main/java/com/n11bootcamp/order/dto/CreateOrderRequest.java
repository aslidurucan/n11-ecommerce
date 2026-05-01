package com.n11bootcamp.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;


@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Sepet boş olamaz")
    @Valid
    private List<OrderItemRequest> items;

    @Valid
    @NotNull(message = "Teslimat adresi zorunlu")
    private ShippingAddressRequest shippingAddress;

    @Valid
    @NotNull(message = "Kart bilgisi zorunlu")
    private CardRequest card;

    // ======================== İÇ SINIF ========================

    @Data
    public static class OrderItemRequest {

        @NotNull(message = "Ürün ID zorunlu")
        @Positive(message = "Ürün ID pozitif olmalı")
        private Long productId;

        @NotNull(message = "Miktar zorunlu")
        @Positive(message = "Miktar en az 1 olmalı")
        private Integer quantity;

        /**
         * unitPrice: Frontend'den gelir ama server-side cross-check yapılır.
         * @NotNull eklendi: null gelirse calculateTotal() NullPointerException fırlatırdı.
         * @Positive: 0 veya negatif fiyat geçersiz.
         *
         * Not: Fiyat manipülasyonu koruması OrderServiceImpl.calculateTotal()'da
         * product-service'ten alınan gerçek fiyatla karşılaştırılacak (ileride).
         */
        @NotNull(message = "Birim fiyat zorunlu")
        @Positive(message = "Birim fiyat pozitif olmalı")
        private BigDecimal unitPrice;

        @NotBlank(message = "Ürün adı zorunlu (snapshot için DB'de NOT NULL)")
        private String productName; // sipariş geçmişi için snapshot — zorunlu
    }
}
