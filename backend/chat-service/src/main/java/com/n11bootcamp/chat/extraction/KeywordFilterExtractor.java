package com.n11bootcamp.chat.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kullanıcının doğal dil sorgusunu yapısal filtreye çevirir.
 *
 * <p><strong>İki katmanlı strateji (Resilience Pattern):</strong></p>
 * <ol>
 *   <li><b>Birincil:</b> Gemini AI ile sorgu analizi (yüksek kalite).</li>
 *   <li><b>Fallback:</b> Gemini erişilemezse veya quota dolduysa,
 *       Türkçe/İngilizce keyword tabanlı extraction.</li>
 * </ol>
 *
 * <p>Bu tasarım sayesinde dış AI servisi çökse bile chat-service
 * kullanılabilir kalır. Production sistemlerde standart yaklaşımdır.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilterExtractor {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /*
     * Veritabanındaki gerçek kategoriler (seed verilerine göre):
     *   Tablet, Ses, Bilgisayar, Spor, Ev & Yaşam, Mutfak,
     *   Outdoor, Kitap, Kozmetik
     *
     * Aşağıdaki keyword listeleri kullanıcının yazdığı serbest metni
     * bu canonical isimlere eşler.
     */
    private static final List<CategoryKeyword> CATEGORY_KEYWORDS = List.of(
            new CategoryKeyword("Bilgisayar", List.of("bilgisayar", "laptop", "macbook", "thinkpad",
                    "notebook", "computer", "pc", "rog", "zephyrus")),
            new CategoryKeyword("Tablet", List.of("tablet", "ipad")),
            new CategoryKeyword("Ses", List.of("ses", "kulaklık", "kulaklik", "headphone", "earphone",
                    "airpods", "earbuds", "hoparlör", "hoparlor", "speaker")),
            new CategoryKeyword("Spor", List.of("spor", "sport", "ayakkabı", "ayakkabi", "shoe",
                    "sneaker", "yoga", "fitness", "koşu", "kosu", "running", "ultraboost",
                    "air force", "air-force")),
            new CategoryKeyword("Ev & Yaşam", List.of("ev & yaşam", "ev yasam", "ev yaşam",
                    "süpürge", "supurge", "vacuum", "dyson", "elektrikli süpürge",
                    "ev aleti", "home")),
            new CategoryKeyword("Mutfak", List.of("mutfak", "kahve", "espresso", "cafetera",
                    "çay bardağı", "cay bardagi", "bardak", "tabak", "kitchen")),
            new CategoryKeyword("Outdoor", List.of("outdoor", "bisiklet", "bicycle", "bike",
                    "dağ bisikleti", "dag bisikleti")),
            new CategoryKeyword("Kitap", List.of("kitap", "book", "roman", "novel", "ders kitabı")),
            new CategoryKeyword("Kozmetik", List.of("kozmetik", "parfüm", "parfum", "perfume",
                    "ruj", "krem", "bakım", "bakim", "cosmetic", "makyaj"))
    );

    // Bilinen marka kelimeleri (TR + global)
    // Veritabanındaki: Samsung, Apple, Sony, Adidas, Nike, Dyson, Asus, Lenovo,
    //                  DeLonghi, Trek, Penguin, Manduka, Chanel, Karaca
    private static final List<String> BRANDS = List.of(
            "samsung", "apple", "sony", "adidas", "nike", "dyson", "asus", "lenovo",
            "delonghi", "trek", "penguin", "manduka", "chanel", "karaca",
            "xiaomi", "huawei", "oppo", "vivo", "nokia",
            "hp", "dell", "msi", "acer", "lg",
            "puma", "reebok", "new balance",
            "lc waikiki", "koton", "mavi", "defacto", "zara", "h&m",
            "philips", "bosch", "siemens", "arçelik", "arcelik", "vestel", "beko"
    );

    // Fiyat pattern'leri (TR)
    // "1000 tl altı", "1000 lira altında"
    private static final Pattern MAX_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira|₺)?\\s*(?:altı|alti|altında|altinda|aşağı|asagi|altina)",
            Pattern.CASE_INSENSITIVE);
    // "1000 tl üstü", "1000 lira üzeri"
    private static final Pattern MIN_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira|₺)?\\s*(?:üstü|ustu|üzeri|uzeri|fazla|yukarı|yukari)",
            Pattern.CASE_INSENSITIVE);
    // "1000-5000 arası"
    private static final Pattern RANGE_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira)?\\s*(?:-|ile|ila|to)\\s*(\\d+)\\s*(?:tl|lira)?\\s*(?:arası|arasi|between)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_PRICE_EN = Pattern.compile(
            "(?:under|below|less than|max(?:imum)?)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE_EN = Pattern.compile(
            "(?:over|above|more than|min(?:imum)?|at least)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_PROMPT = """
        Sen bir e-ticaret sitesinin ürün arama asistanısın.
        Kullanıcının Türkçe veya İngilizce yazdığı sorgudan SADECE şu 4 alanı çıkar:
        - category: ürün kategorisi. Şu kategorilerden birini seç (yoksa null):
          [Bilgisayar, Tablet, Ses, Spor, Ev & Yaşam, Mutfak, Outdoor, Kitap, Kozmetik].
        - brand: marka adı (örn. "Apple", "Samsung", "Nike", "Sony"). Bilinmiyorsa null.
        - minPrice: minimum fiyat (TL, sayı). Belirtilmemişse null.
        - maxPrice: maksimum fiyat (TL, sayı). Belirtilmemişse null.

        Kurallar:
        - "1000 tl altı" → maxPrice: 1000
        - "5000 tl üstü" → minPrice: 5000
        - "1000-5000 arası" → minPrice: 1000, maxPrice: 5000
        - "elektronik ürünler" → category: null (Elektronik kategorisi yok)
        - "laptop" → category: "Bilgisayar"
        - "kulaklık" → category: "Ses"
        - "ayakkabı" → category: "Spor"
        - Renk veya beden gibi diğer alanları YOK SAY.
        - Hiçbir alan yorum eklemeden, sadece JSON döndür.
        - Çıktı şu yapıda olmalı: {"category": ..., "brand": ..., "minPrice": ..., "maxPrice": ...}

        Kullanıcı sorgusu: "%s"

        SADECE JSON döndür, başka hiçbir şey yazma.
        """;

    /**
     * Sorguyu analiz edip filter çıkarır.
     */
    public ExtractedFilter extract(String userQuery) {
        // 1. Önce Gemini'yi dene
        try {
            String prompt = SYSTEM_PROMPT.formatted(escapeQuotes(userQuery));
            String rawResponse = geminiClient.generate(prompt);
            ExtractedFilter aiResult = parseGeminiResponse(rawResponse);
            log.info("Gemini extraction success: {}", aiResult);
            return aiResult;
        } catch (Exception e) {
            log.warn("Gemini extraction failed ({}), keyword fallback'e geçiliyor",
                    e.getClass().getSimpleName());
        }

        // 2. Fallback — keyword tabanlı extraction
        ExtractedFilter fallbackResult = keywordExtract(userQuery);
        log.info("Keyword fallback extraction: {}", fallbackResult);
        return fallbackResult;
    }

    private ExtractedFilter parseGeminiResponse(String rawResponse) throws JsonProcessingException {
        JsonNode json = objectMapper.readTree(rawResponse);
        return new ExtractedFilter(
                nullIfBlank(json.path("category").asText(null)),
                nullIfBlank(json.path("brand").asText(null)),
                parsePrice(json.path("minPrice")),
                parsePrice(json.path("maxPrice"))
        );
    }

    private ExtractedFilter keywordExtract(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return new ExtractedFilter(null, null, null, null);
        }
        String lower = userQuery.toLowerCase(new Locale("tr", "TR"));

        // Kategori
        String category = null;
        for (CategoryKeyword ck : CATEGORY_KEYWORDS) {
            for (String kw : ck.keywords()) {
                if (lower.contains(kw)) {
                    category = ck.canonicalName();
                    break;
                }
            }
            if (category != null) break;
        }

        // Marka
        String brand = null;
        for (String b : BRANDS) {
            if (lower.contains(b)) {
                brand = capitalize(b);
                break;
            }
        }

        // Fiyat
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        Matcher rangeMatcher = RANGE_PRICE_TR.matcher(lower);
        if (rangeMatcher.find()) {
            minPrice = new BigDecimal(rangeMatcher.group(1));
            maxPrice = new BigDecimal(rangeMatcher.group(2));
        } else {
            Matcher maxTr = MAX_PRICE_TR.matcher(lower);
            if (maxTr.find()) maxPrice = new BigDecimal(maxTr.group(1));
            Matcher maxEn = MAX_PRICE_EN.matcher(lower);
            if (maxEn.find() && maxPrice == null) maxPrice = new BigDecimal(maxEn.group(1));

            Matcher minTr = MIN_PRICE_TR.matcher(lower);
            if (minTr.find()) minPrice = new BigDecimal(minTr.group(1));
            Matcher minEn = MIN_PRICE_EN.matcher(lower);
            if (minEn.find() && minPrice == null) minPrice = new BigDecimal(minEn.group(1));
        }

        return new ExtractedFilter(category, brand, minPrice, maxPrice);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) {
                sb.append(c);
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s.trim();
    }

    private BigDecimal parsePrice(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isBlank() || "null".equalsIgnoreCase(text)) return null;
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public record ExtractedFilter(
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        public boolean isEmpty() {
            return category == null && brand == null
                    && minPrice == null && maxPrice == null;
        }
    }

    private record CategoryKeyword(String canonicalName, List<String> keywords) {}
}