package com.n11bootcamp.chat.extraction;

import com.n11bootcamp.chat.service.ProductMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordFilterExtractor implements FilterExtractor {

    private final ProductMetadataService metadataService;

    private static final Pattern MAX_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira|₺)?\\s*(?:altı|alti|altında|altinda|aşağı|asagi|altina)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira|₺)?\\s*(?:üstü|ustu|üzeri|uzeri|fazla|yukarı|yukari)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_PRICE_TR = Pattern.compile(
            "(\\d+)\\s*(?:tl|lira)?\\s*(?:-|ile|ila|to)\\s*(\\d+)\\s*(?:tl|lira)?\\s*(?:arası|arasi|between)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_PRICE_EN = Pattern.compile(
            "(?:under|below|less than|max(?:imum)?)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE_EN = Pattern.compile(
            "(?:over|above|more than|min(?:imum)?|at least)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public ExtractedFilter extract(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return new ExtractedFilter(null, null, null, null);
        }
        String lower = userQuery.toLowerCase(new Locale("tr", "TR"));

        String category = extractCategory(lower);
        String brand = extractBrand(lower);
        BigDecimal[] prices = extractPrices(lower);

        ExtractedFilter result = new ExtractedFilter(category, brand, prices[0], prices[1]);
        log.info("Keyword extracted filter: {}", result);
        return result;
    }

    private String extractCategory(String lower) {
        for (String category : metadataService.getCategories()) {
            if (lower.contains(category.toLowerCase(new Locale("tr", "TR")))) return category;
        }
        return null;
    }

    private String extractBrand(String lower) {
        for (String brand : metadataService.getBrands()) {
            if (lower.contains(brand.toLowerCase(Locale.ROOT))) return brand;
        }
        return null;
    }

    private BigDecimal[] extractPrices(String lower) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        Matcher rangeMatcher = RANGE_PRICE_TR.matcher(lower);
        if (rangeMatcher.find()) {
            return new BigDecimal[]{
                    new BigDecimal(rangeMatcher.group(1)),
                    new BigDecimal(rangeMatcher.group(2))
            };
        }

        Matcher maxTr = MAX_PRICE_TR.matcher(lower);
        if (maxTr.find()) maxPrice = new BigDecimal(maxTr.group(1));
        Matcher maxEn = MAX_PRICE_EN.matcher(lower);
        if (maxEn.find() && maxPrice == null) maxPrice = new BigDecimal(maxEn.group(1));

        Matcher minTr = MIN_PRICE_TR.matcher(lower);
        if (minTr.find()) minPrice = new BigDecimal(minTr.group(1));
        Matcher minEn = MIN_PRICE_EN.matcher(lower);
        if (minEn.find() && minPrice == null) minPrice = new BigDecimal(minEn.group(1));

        return new BigDecimal[]{minPrice, maxPrice};
    }
}
