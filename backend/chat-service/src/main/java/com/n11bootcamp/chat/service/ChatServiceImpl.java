package com.n11bootcamp.chat.service;

import com.n11bootcamp.chat.client.ProductClient;
import com.n11bootcamp.chat.client.ProductPageResponse;
import com.n11bootcamp.chat.dto.ChatRequest;
import com.n11bootcamp.chat.dto.ChatResponse;
import com.n11bootcamp.chat.extraction.ExtractedFilter;
import com.n11bootcamp.chat.extraction.FilterExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_RESULTS = 6;

    private final FilterExtractor filterExtractor;
    private final ProductClient productClient;

    @Override
    public ChatResponse search(ChatRequest request, String language) {
        String query = request.query().trim();
        log.info("Chat search: query='{}', lang={}", query, language);

        ExtractedFilter filter = filterExtractor.extract(query);

        ProductPageResponse page = productClient.listProducts(
                language,
                filter.category(),
                filter.brand(),
                filter.minPrice(),
                filter.maxPrice(),
                0,
                MAX_RESULTS
        );

        List<ProductPageResponse.ProductDto> products = page.content() != null
                ? page.content() : List.of();

        String reply = composeReply(filter, products.size(), page.totalElements(), language);

        List<ChatResponse.ProductSummary> summaries = products.stream()
                .map(this::toSummary)
                .toList();

        return new ChatResponse(
                reply,
                new ChatResponse.InterpretedFilter(
                        filter.category(),
                        filter.brand(),
                        filter.minPrice() != null ? filter.minPrice().doubleValue() : null,
                        filter.maxPrice() != null ? filter.maxPrice().doubleValue() : null
                ),
                summaries
        );
    }

    private String composeReply(ExtractedFilter filter, int shown, Long total, String language) {
        boolean isTr = language == null || language.toLowerCase(Locale.ROOT).startsWith("tr");

        if (shown == 0) {
            return isTr
                    ? "Aramanıza uyan ürün bulamadım. Farklı bir sorgu denemek ister misin?"
                    : "I couldn't find any matching products. Want to try a different query?";
        }

        if (filter.isEmpty()) {
            return isTr
                    ? "Sorgundan belirli bir filtre çıkaramadım, ama sana popüler ürünlerden gösteriyorum:"
                    : "I couldn't extract specific filters, but here are some popular products:";
        }

        long totalCount = total != null ? total : shown;
        return isTr
                ? "Aramana uyan %d ürün buldum. İlk %d tanesini gösteriyorum:".formatted(totalCount, shown)
                : "Found %d matching products. Showing first %d:".formatted(totalCount, shown);
    }

    private ChatResponse.ProductSummary toSummary(ProductPageResponse.ProductDto dto) {
        return new ChatResponse.ProductSummary(
                dto.id(),
                dto.name(),
                dto.brand(),
                dto.category(),
                dto.basePrice() != null ? dto.basePrice().doubleValue() : null,
                dto.imageUrl()
        );
    }
}
