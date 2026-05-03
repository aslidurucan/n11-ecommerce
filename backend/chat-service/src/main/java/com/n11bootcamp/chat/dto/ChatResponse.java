package com.n11bootcamp.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Chatbot yanıtı: doğal dil cevap + ürün listesi + yorumlanan filtre")
public record ChatResponse(

        @Schema(example = "Şu 3 ürünü buldum:",
                description = "Kullanıcıya gösterilecek doğal dil cevap")
        String reply,

        @Schema(description = "LLM'in sorgudan çıkardığı filtre (şeffaflık için)")
        InterpretedFilter interpretedFilter,

        @Schema(description = "Filtreye uyan ürünler")
        List<ProductSummary> products
) {

    public record InterpretedFilter(
            String category,
            String brand,
            Double minPrice,
            Double maxPrice
    ) {
    }

    public record ProductSummary(
            Long id,
            String name,
            String brand,
            String category,
            Double basePrice,
            String imageUrl
    ) {
    }
}