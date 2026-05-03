package com.n11bootcamp.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Kullanıcı tarafından girilen doğal dil arama sorgusu")
public record ChatRequest(

        @NotBlank(message = "Sorgu boş olamaz")
        @Size(max = 500, message = "Sorgu en fazla 500 karakter olabilir")
        @Schema(example = "1000 TL altı elektronik ürünler",
                description = "Kullanıcının yazdığı serbest metin")
        String query
) {
}