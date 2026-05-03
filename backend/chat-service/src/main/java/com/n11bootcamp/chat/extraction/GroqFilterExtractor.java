package com.n11bootcamp.chat.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.chat.service.ProductMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "groq.api-key")
@RequiredArgsConstructor
public class GroqFilterExtractor implements FilterExtractor {

    private static final String CHAT_PATH = "/openai/v1/chat/completions";

    private final RestClient groqRestClient;
    private final KeywordFilterExtractor fallback;
    private final ProductMetadataService metadataService;
    private final ObjectMapper objectMapper;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    @Override
    public ExtractedFilter extract(String userQuery) {
        try {
            String rawResponse = callGroq(userQuery);
            return parseResponse(rawResponse);
        } catch (Exception e) {
            log.warn("Groq API failed, falling back to keyword extractor: {}", e.getMessage());
            return fallback.extract(userQuery);
        }
    }

    private String buildSystemPrompt() {
        List<String> categories = metadataService.getCategories();
        List<String> brands = metadataService.getBrands();

        String categoryList = categories.isEmpty() ? "bilinmiyor" : String.join(", ", categories);
        String brandList = brands.isEmpty() ? "bilinmiyor" : String.join(", ", brands);

        return """
                Sen bir e-ticaret arama filtresi çıkarıcısısın.
                Kullanıcının Türkçe veya İngilizce sorgusundan aşağıdaki alanları çıkar ve SADECE JSON döndür.

                Geçerli kategoriler (sadece bunlardan birini seç veya null):
                %s

                Geçerli markalar (sadece bunlardan birini seç veya null):
                %s

                JSON formatı:
                {
                  "category": "..." | null,
                  "brand": "..." | null,
                  "minPrice": 1000 | null,
                  "maxPrice": 5000 | null
                }

                Kural: Emin değilsen null yaz. Hiçbir zaman tahmin etme.
                """.formatted(categoryList, brandList);
    }

    private String callGroq(String userQuery) {
        GroqRequest body = new GroqRequest(
                model,
                0,
                new ResponseFormat("json_object"),
                List.of(
                        new Message("system", buildSystemPrompt()),
                        new Message("user", userQuery)
                )
        );

        String response = groqRestClient.post()
                .uri(CHAT_PATH)
                .body(body)
                .retrieve()
                .body(String.class);

        log.debug("Groq raw response: {}", response);
        return response;
    }

    private record GroqRequest(String model, int temperature, ResponseFormat response_format, List<Message> messages) {}
    private record ResponseFormat(String type) {}
    private record Message(String role, String content) {}

    private ExtractedFilter parseResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String content = root.at("/choices/0/message/content").asText();

        log.debug("Groq extracted content: {}", content);

        JsonNode result = objectMapper.readTree(content);

        String category = nullableText(result, "category");
        String brand    = nullableText(result, "brand");
        BigDecimal minPrice = nullableDecimal(result, "minPrice");
        BigDecimal maxPrice = nullableDecimal(result, "maxPrice");

        ExtractedFilter filter = new ExtractedFilter(category, brand, minPrice, maxPrice);
        log.info("Groq extracted filter: {}", filter);
        return filter;
    }

    private String nullableText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private BigDecimal nullableDecimal(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? new BigDecimal(node.get(field).asText()) : null;
    }
}
